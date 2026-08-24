package titus.expenseassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
import java.util.Calendar;

public class MainActivity extends Activity {
    private final int RED = Color.rgb(217,45,32), GREEN = Color.rgb(26,127,75), INK = Color.rgb(28,28,30), MUTED = Color.rgb(105,105,110);
    private BudgetStore store;
    private LinearLayout root;
    private String filterDate;
    private String filterSource;

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
        SecretStore secrets=new SecretStore(this); TextView ai=text(secrets.hasApiKey()?"✦ Gemini đang bật • Chạm để đổi API key":"✦ Bật phân loại thông minh bằng Gemini",14,secrets.hasApiKey()?GREEN:RED,true);ai.setPadding(0,dp(10),0,dp(5));ai.setOnClickListener(v->geminiDialog());root.addView(ai);
        Button sourceButton = new Button(this); sourceButton.setText("NGUỒN TIỀN: " + store.defaultSource()); sourceButton.setTextColor(INK); sourceButton.setOnClickListener(v -> sourceDialog()); root.addView(sourceButton, top(4));
        EditText input = new EditText(this); input.setHint("Ví dụ: ăn trưa 30k"); input.setTextSize(18); input.setSingleLine(true); input.setPadding(dp(16),dp(14),dp(16),dp(14)); input.setBackground(card(Color.WHITE, 0xffdddddf)); root.addView(input, top(10));
        TextView preview = text("App sẽ tự đọc số tiền và phân loại", 14, MUTED, false); root.addView(preview, top(8));
        Button add = new Button(this); add.setText("THÊM CHI TIÊU"); add.setTextColor(Color.WHITE); add.setTypeface(Typeface.DEFAULT_BOLD); add.setBackground(card(RED, RED)); root.addView(add, top(12));
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ ExpenseParser.Result p=ExpenseParser.parse(s.toString()); preview.setText(p.amount>0 ? p.category+"  •  "+Format.money(p.amount) : "App sẽ tự đọc số tiền và phân loại"); } public void afterTextChanged(Editable e){} });
        add.setOnClickListener(v -> { String raw=input.getText().toString(); ExpenseParser.Result p=ExpenseParser.parse(raw); if(p.amount<=0){input.setError("Nhập thêm số tiền, ví dụ 30k");return;} String selectedSource=p.source.isEmpty()?store.defaultSource():p.source; add.setEnabled(false);add.setText(secrets.hasApiKey()?"GEMINI ĐANG PHÂN LOẠI…":"ĐANG LƯU…");GeminiClassifier.classifyAsync(this,raw,p.category,(category,usedAi)->{ExpenseParser.Result smart=new ExpenseParser.Result(p.amount,category,p.note,selectedSource);store.add(smart,raw,selectedSource);NotificationHelper.refresh(this);Toast.makeText(this,"Đã thêm vào "+category+" • "+selectedSource,Toast.LENGTH_SHORT).show();draw();}); });

        String historyTitle = filterDate == null && filterSource == null ? "Chi tiêu trong 6 tháng" : "Lịch sử đã lọc";
        root.addView(text(historyTitle, 22, INK, true), top(28));
        LinearLayout filterRow = new LinearLayout(this); filterRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button filter = new Button(this); filter.setText(filterDate == null ? "NGÀY: TẤT CẢ" : "NGÀY: " + filterDate); filter.setTextColor(RED); filter.setOnClickListener(v -> showDateFilter()); filterRow.addView(filter, new LinearLayout.LayoutParams(0, -2, 1));
        Button sourceFilter = new Button(this); sourceFilter.setText(filterSource == null ? "NGUỒN: TẤT CẢ" : "NGUỒN: " + filterSource); sourceFilter.setTextColor(RED); sourceFilter.setOnClickListener(v -> filterSourceDialog()); filterRow.addView(sourceFilter, new LinearLayout.LayoutParams(0, -2, 1));
        if (filterDate != null || filterSource != null) { Button clear = new Button(this); clear.setText("XÓA"); clear.setTextColor(MUTED); clear.setOnClickListener(v -> { filterDate=null; filterSource=null; draw(); }); filterRow.addView(clear, new LinearLayout.LayoutParams(-2, -2)); }
        root.addView(filterRow, top(4));
        JSONArray items = store.items();
        boolean found = false;
        String activeDate = "";
        for (int i=items.length()-1; i>=0; i--) try {
            JSONObject item = items.getJSONObject(i);
            String date = item.getString("date");
            if (filterDate != null && !filterDate.equals(date)) continue;
            if (filterSource != null && !filterSource.equals(item.optString("source", "Tiền mặt"))) continue;
            found = true;
            if (!date.equals(activeDate)) {
                activeDate = date;
                root.addView(dayHeader(date, dayTotal(items, date)), top(16));
            }
            root.addView(expense(item), top(7));
        } catch(Exception ignored){}
        if (!found) root.addView(text(filterDate == null ? "Chưa có khoản chi trong 6 tháng gần đây." : "Ngày này chưa có khoản chi.",15,MUTED,false),top(12));
    }

    private void showDateFilter() {
        Calendar today = Calendar.getInstance();
        LocalDate selected = filterDate == null ? LocalDate.now() : LocalDate.parse(filterDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            filterDate = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day); draw();
        }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        Calendar minimum = Calendar.getInstance(); minimum.add(Calendar.MONTH, -5); minimum.set(Calendar.DAY_OF_MONTH, 1);
        dialog.getDatePicker().setMinDate(minimum.getTimeInMillis()); dialog.getDatePicker().setMaxDate(today.getTimeInMillis()); dialog.show();
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
        words.addView(text(o.getString("note"),16,INK,true)); words.addView(text(o.getString("category")+" • "+o.optString("source", "Tiền mặt")+" • "+(LocalDate.now().toString().equals(o.getString("date"))?"Hôm nay":o.getString("date")),13,MUTED,false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1)); row.addView(text("−"+Format.money(o.getLong("amount")),16,RED,true)); return row;
    }

    private LinearLayout stat(String label,String value){ LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(label,12,MUTED,true));b.addView(text(value,18,INK,true));return b; }
    private void budgetDialog(){ EditText e=new EditText(this);e.setHint("Ví dụ: 10.000.000");e.setInputType(2);e.setText(store.budget()==0?"":String.valueOf(store.budget()));e.setSelectAllOnFocus(true);int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Ngân sách tháng").setMessage("Tui sẽ chia số tiền còn lại đều cho các ngày còn lại.").setView(wrap).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{try{long value=Long.parseLong(e.getText().toString().replaceAll("\\D",""));store.setBudget(value);NotificationHelper.refresh(this);draw();}catch(Exception x){Toast.makeText(this,"Số tiền chưa hợp lệ",Toast.LENGTH_SHORT).show();}}).show(); }
    private void geminiDialog(){SecretStore s=new SecretStore(this);EditText e=new EditText(this);e.setHint("Dán Gemini API key");e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Gemini phân loại thông minh").setMessage("Key được mã hóa bằng Android Keystore và chỉ lưu trên máy. Khi Gemini lỗi hoặc mất mạng, app dùng bộ lọc offline.").setView(wrap).setNegativeButton("Hủy",null).setNeutralButton("Xóa key",(d,w)->{s.setApiKey("");draw();}).setPositiveButton("Lưu",(d,w)->{if(e.getText().toString().trim().isEmpty()){Toast.makeText(this,"Chưa có API key",Toast.LENGTH_SHORT).show();return;}s.setApiKey(e.getText().toString());Toast.makeText(this,"Đã bật Gemini",Toast.LENGTH_SHORT).show();draw();}).show();}
    private void sourceDialog(){java.util.List<String> values=store.sources();String current=store.defaultSource();int checked=Math.max(0,values.indexOf(current));new AlertDialog.Builder(this).setTitle("Nguồn tiền mặc định").setSingleChoiceItems(values.toArray(new String[0]),checked,(d,which)->{store.setDefaultSource(values.get(which));d.dismiss();draw();}).setNeutralButton("Thêm nguồn",(d,w)->customSourceDialog()).setNegativeButton("Hủy",null).show();}
    private void filterSourceDialog(){java.util.List<String> values=store.sources();values.add(0,"Tất cả nguồn");int checked=filterSource==null?0:Math.max(0,values.indexOf(filterSource));new AlertDialog.Builder(this).setTitle("Lọc theo nguồn chi").setSingleChoiceItems(values.toArray(new String[0]),checked,(d,which)->{filterSource=which==0?null:values.get(which);d.dismiss();draw();}).setNegativeButton("Hủy",null).show();}
    private void customSourceDialog(){EditText e=new EditText(this);e.setHint("Ví dụ: Ví MoMo, thẻ Visa...");e.setSingleLine(true);int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Thêm nguồn tiền").setView(wrap).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{String value=e.getText().toString().trim();if(!value.isEmpty()){store.addSource(value);store.setDefaultSource(value);draw();}}).show();}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable card(int fill,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(14));g.setStroke(dp(1),stroke);return g;}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(px);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
