package titus.expenseassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int RED = Color.rgb(217,45,32), GREEN = Color.rgb(26,127,75), INK = Color.rgb(28,28,30), MUTED = Color.rgb(105,105,110);
    private BudgetStore store;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); store = new BudgetStore(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        startForegroundService(new Intent(this, BudgetNotificationService.class));
        draw();
        if (store.budget() == 0) root.post(this::budgetDialog);
    }

    @Override protected void onResume() { super.onResume(); if (root != null) draw(); }

    private void draw() {
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,pad); root.setBackgroundColor(Color.rgb(248,248,250));
        scroll.addView(root); setContentView(scroll);

        TextView brand = text("TRỢ LÝ CHI TIÊU", 14, RED, true); brand.setLetterSpacing(.08f); root.addView(brand);
        root.addView(text("Tháng này còn", 16, MUTED, false), top(20));
        TextView remain = text(Format.money(store.remaining()), 36, INK, true); root.addView(remain, top(2));
        TextView edit = text("Ngân sách " + Format.money(store.budget()) + "  •  Chạm để sửa", 14, MUTED, false); edit.setPadding(0,dp(8),0,dp(12)); edit.setOnClickListener(v -> budgetDialog()); root.addView(edit);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000);
        int progress = store.budget() == 0 ? 0 : (int)Math.min(1000, store.monthSpent() * 1000 / store.budget()); bar.setProgress(progress); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(progress >= 1000 ? RED : GREEN)); root.addView(bar, new LinearLayout.LayoutParams(-1,dp(9)));

        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setPadding(0,dp(18),0,dp(18));
        stats.addView(stat("ĐÃ CHI THÁNG", Format.money(store.monthSpent())), new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat("HÔM NAY", Format.money(store.todaySpent())), new LinearLayout.LayoutParams(0,-2,1));
        root.addView(stats);

        boolean over = store.todaySpent() > store.todayAllowance() && store.todaySpent() > 0;
        TextView allowance = text((over ? "⚠ Vượt mức gợi ý hôm nay\n" : "Mức có thể chi hôm nay\n") + Format.money(store.todayAllowance()), 18, over ? RED : GREEN, true);
        allowance.setPadding(dp(16),dp(15),dp(16),dp(15)); allowance.setBackground(card(over ? 0xffffeeee : 0xffeaf7ef, over ? RED : GREEN)); root.addView(allowance);

        root.addView(text("Nhập nhanh", 22, INK, true), top(26));
        EditText input = new EditText(this); input.setHint("Ví dụ: ăn trưa 30k"); input.setTextSize(18); input.setSingleLine(true); input.setPadding(dp(16),dp(14),dp(16),dp(14)); input.setBackground(card(Color.WHITE, 0xffdddddf)); root.addView(input, top(10));
        TextView preview = text("App sẽ tự đọc số tiền và phân loại", 14, MUTED, false); root.addView(preview, top(8));
        Button add = new Button(this); add.setText("THÊM CHI TIÊU"); add.setTextColor(Color.WHITE); add.setTypeface(Typeface.DEFAULT_BOLD); add.setBackground(card(RED, RED)); root.addView(add, top(12));
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ ExpenseParser.Result p=ExpenseParser.parse(s.toString()); preview.setText(p.amount>0 ? p.category+"  •  "+Format.money(p.amount) : "App sẽ tự đọc số tiền và phân loại"); } public void afterTextChanged(Editable e){} });
        add.setOnClickListener(v -> { String raw=input.getText().toString(); ExpenseParser.Result p=ExpenseParser.parse(raw); if(p.amount<=0){input.setError("Nhập thêm số tiền, ví dụ 30k");return;} store.add(p,raw); NotificationHelper.refresh(this); Toast.makeText(this,"Đã thêm vào "+p.category,Toast.LENGTH_SHORT).show(); draw(); });

        root.addView(text("Chi tiêu trong tháng", 22, INK, true), top(28));
        JSONArray items = store.items();
        if (items.length() == 0) root.addView(text("Chưa có khoản chi nào trong tháng.",15,MUTED,false),top(10));
        String activeDate = "";
        for (int i=items.length()-1; i>=0; i--) try {
            JSONObject item = items.getJSONObject(i);
            String date = item.getString("date");
            if (!date.equals(activeDate)) {
                activeDate = date;
                root.addView(dayHeader(date, dayTotal(items, date)), top(16));
            }
            root.addView(expense(item), top(7));
        } catch(Exception ignored){}
    }

    private View dayHeader(String isoDate, long total) {
        LocalDate date = LocalDate.parse(isoDate);
        String label = date.equals(LocalDate.now()) ? "Hôm nay" : date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM", new Locale("vi", "VN")));
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.addView(text(label,15,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        row.addView(text("Tổng " + Format.money(total),14,MUTED,true));
        return row;
    }

    private long dayTotal(JSONArray items, String date) {
        long total = 0;
        for (int i=0; i<items.length(); i++) try { JSONObject o=items.getJSONObject(i); if(date.equals(o.getString("date"))) total += o.getLong("amount"); } catch(Exception ignored){}
        return total;
    }

    private View expense(JSONObject o) throws Exception {
        LinearLayout row = new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL); row.setPadding(dp(14),dp(12),dp(14),dp(12)); row.setBackground(card(Color.WHITE,0xffe5e5e8));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(o.getString("note"),16,INK,true)); words.addView(text(o.getString("category")+" • "+(LocalDate.now().toString().equals(o.getString("date"))?"Hôm nay":o.getString("date")),13,MUTED,false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1)); row.addView(text("−"+Format.money(o.getLong("amount")),16,RED,true)); return row;
    }

    private LinearLayout stat(String label,String value){ LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(label,12,MUTED,true));b.addView(text(value,18,INK,true));return b; }
    private void budgetDialog(){ EditText e=new EditText(this);e.setHint("Ví dụ: 10.000.000");e.setInputType(2);e.setText(store.budget()==0?"":String.valueOf(store.budget()));e.setSelectAllOnFocus(true);int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Ngân sách tháng").setMessage("Tui sẽ chia số tiền còn lại đều cho các ngày còn lại.").setView(wrap).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{try{long value=Long.parseLong(e.getText().toString().replaceAll("\\D",""));store.setBudget(value);NotificationHelper.refresh(this);draw();}catch(Exception x){Toast.makeText(this,"Số tiền chưa hợp lệ",Toast.LENGTH_SHORT).show();}}).show(); }
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable card(int fill,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(14));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(px);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
