package titus.expenseassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private int RED, GREEN, INK, MUTED, PAGE, CARD;
    private BudgetStore store;
    private LinearLayout root;
    private int tab = 0;
    private String filterStartDate, filterEndDate, filterCategory;
    private final Set<String> filterSources = new LinkedHashSet<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new BudgetStore(this);
        updatePalette();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        startForegroundService(new Intent(this, BudgetNotificationService.class));
        draw();
        if (store.budget() == 0) root.post(this::budgetDialog);
    }

    @Override protected void onResume() { super.onResume(); if (root != null) draw(); }

    private void updatePalette() {
        SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
        String theme = p.getString("theme", "system");
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = "dark".equals(theme) || ("system".equals(theme) && night);
        RED = Color.rgb(217,45,32); GREEN = dark ? Color.rgb(65,190,116) : Color.rgb(26,127,75); INK = dark ? Color.WHITE : Color.rgb(28,28,30); MUTED = dark ? Color.rgb(185,185,190) : Color.rgb(105,105,110); PAGE = dark ? Color.rgb(20,20,22) : Color.rgb(248,248,250); CARD = dark ? Color.rgb(38,38,42) : Color.WHITE;
    }

    private void draw() {
        updatePalette();
        int pad = dp(20);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,dp(88)); root.setBackgroundColor(PAGE);
        scroll.addView(root);
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(PAGE);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1,-1); scrollParams.bottomMargin = dp(72); frame.addView(scroll, scrollParams);
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1,dp(64),Gravity.BOTTOM); navParams.leftMargin = dp(12); navParams.rightMargin = dp(12); navParams.bottomMargin = dp(8); frame.addView(bottomNav(), navParams);
        setContentView(frame);
        root.addView(header());
        if (tab == 0) drawHome(scroll); else drawHistory();
    }

    private View header() {
        LinearLayout bar = new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("TRỢ LÝ CHI TIÊU", 14, RED, true); brand.setLetterSpacing(.08f); bar.addView(brand, new LinearLayout.LayoutParams(0,-2,1));
        SecretStore secrets = new SecretStore(this);
        int geminiColor = secrets.hasApiKey() ? GREEN : Color.rgb(232,128,24);
        LinearLayout geminiBox = new LinearLayout(this); geminiBox.setGravity(Gravity.CENTER_VERTICAL); geminiBox.setPadding(dp(4),0,dp(6),0); geminiBox.setContentDescription("Gemini"); geminiBox.setOnClickListener(v -> settingsDialog());
        TextView gemini = text("✦", 25, geminiColor, true); gemini.setGravity(Gravity.CENTER); geminiBox.addView(gemini, new LinearLayout.LayoutParams(dp(34),dp(42)));
        TextView geminiLabel = text("Gemini", 13, geminiColor, true); geminiLabel.setGravity(Gravity.CENTER_VERTICAL); geminiBox.addView(geminiLabel, new LinearLayout.LayoutParams(-2,dp(42)));
        bar.addView(geminiBox, new LinearLayout.LayoutParams(-2,dp(42)));
        TextView settings = text("⚙", 23, INK, false); settings.setGravity(Gravity.CENTER); settings.setContentDescription("Tùy chỉnh"); settings.setOnClickListener(v -> settingsDialog()); bar.addView(settings, new LinearLayout.LayoutParams(dp(42),dp(42)));
        return bar;
    }

    private void drawHome(ScrollView scroll) {
        root.addView(text("Tháng này còn", 16, MUTED, false), top(20));
        root.addView(text(Format.money(store.remaining()), 36, INK, true), top(2));
        TextView edit = text("Ngân sách " + Format.money(store.budget()) + "  •  Chạm để sửa", 14, MUTED, false); edit.setPadding(0,dp(8),0,dp(12)); edit.setOnClickListener(v -> budgetDialog()); root.addView(edit);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); int progress = store.budget() == 0 ? 0 : (int)Math.min(1000, store.monthSpent() * 1000 / store.budget()); bar.setProgress(progress); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(progress >= 1000 ? RED : GREEN)); root.addView(bar, new LinearLayout.LayoutParams(-1,dp(9)));
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setPadding(0,dp(18),0,dp(18)); stats.addView(stat("ĐÃ CHI THÁNG", Format.money(store.monthSpent())), new LinearLayout.LayoutParams(0,-2,1)); stats.addView(stat("HÔM NAY", Format.money(store.todaySpent())), new LinearLayout.LayoutParams(0,-2,1)); root.addView(stats);
        boolean over = store.todaySpent() > store.todayAllowance() && store.todaySpent() > 0; TextView allowance = text((over ? "⚠ Vượt mức gợi ý hôm nay\n" : "Mức có thể chi hôm nay\n") + Format.money(store.todayAllowance()), 18, over ? RED : GREEN, true); allowance.setPadding(dp(16),dp(15),dp(16),dp(15)); allowance.setBackground(card(over ? 0xffffeeee : (darkGreen()), over ? RED : GREEN)); root.addView(allowance);
        root.addView(text("Nhập nhanh", 22, INK, true), top(26));
        EditText input = new EditText(this); input.setHint("Ví dụ: ăn trưa 30k • thẻ Tech"); input.setTextSize(18); input.setSingleLine(true); input.setPadding(dp(16),dp(14),dp(16),dp(14)); input.setBackground(card(CARD, 0xffdddddf)); root.addView(input, top(10));
        input.setOnFocusChangeListener((v, focused) -> { if (focused) { Runnable reveal = () -> { Rect rect = new Rect(); input.getDrawingRect(rect); scroll.offsetDescendantRectToMyCoords(input, rect); rect.bottom += dp(180); scroll.requestChildRectangleOnScreen(input, rect, true); }; input.postDelayed(reveal, 350); input.postDelayed(reveal, 900); } });
        TextView preview = text("Gemini sẽ tự phân loại mục tiêu và nguồn tiền", 14, MUTED, false); root.addView(preview, top(8));
        Button add = new Button(this); add.setText("THÊM CHI TIÊU"); add.setTextColor(Color.WHITE); add.setTypeface(Typeface.DEFAULT_BOLD); add.setBackground(card(RED, RED)); root.addView(add, top(12));
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ ExpenseParser.Result p=ExpenseParser.parse(s.toString()); preview.setText(p.amount>0 ? p.category+" • "+(p.source.isEmpty()?store.defaultSource():p.source)+" • "+Format.money(p.amount) : "Gemini sẽ tự phân loại mục tiêu và nguồn tiền"); } public void afterTextChanged(Editable e){} });
        SecretStore secrets = new SecretStore(this);
        add.setOnClickListener(v -> { String raw=input.getText().toString(); ExpenseParser.Result p=ExpenseParser.parse(raw); if(p.amount<=0){input.setError("Nhập thêm số tiền, ví dụ 30k");return;} String fallbackSource=p.source.isEmpty()?store.defaultSource():p.source; add.setEnabled(false); add.setText(secrets.hasApiKey()?"GEMINI ĐANG PHÂN LOẠI…":"ĐANG LƯU…"); GeminiClassifier.classifyAsync(this,raw,p.category,fallbackSource,(result,usedAi)->{ExpenseParser.Result smart=new ExpenseParser.Result(p.amount,result.category,p.note,result.source);store.add(smart,raw,result.source);NotificationHelper.refresh(this);Toast.makeText(this,"Đã thêm vào "+result.category+" • "+result.source,Toast.LENGTH_SHORT).show();draw();}); });
        drawToday();
    }

    private void drawToday() {
        root.addView(text("Giao dịch hôm nay", 22, INK, true), top(28)); JSONArray items = store.items(); String today = LocalDate.now().toString(); boolean found = false;
        for (int i=items.length()-1;i>=0;i--) try { JSONObject o=items.getJSONObject(i); if(today.equals(o.getString("date"))){found=true; root.addView(expense(o), top(8));} } catch(Exception ignored){}
        if(!found) root.addView(text("Chưa có khoản chi hôm nay.",15,MUTED,false),top(12));
    }

    private void drawHistory() {
        root.addView(text("Chi tiêu trong 6 tháng", 22, INK, true), top(22));
        LinearLayout filters = new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        Button dateButton = new Button(this); dateButton.setText(dateFilterLabel()); dateButton.setTextColor(RED); dateButton.setOnClickListener(v->showDateFilter()); filters.addView(dateButton,new LinearLayout.LayoutParams(0,-2,1));
        Button source = new Button(this); source.setText(filterSources.isEmpty()?"NGUỒN: TẤT CẢ":"NGUỒN: "+filterSources.size()+" ĐÃ CHỌN"); source.setTextColor(RED); source.setOnClickListener(v->filterSourceDialog()); filters.addView(source,new LinearLayout.LayoutParams(0,-2,1));
        Button category = new Button(this); category.setText(filterCategory==null?"MỤC: TẤT CẢ":"MỤC: "+filterCategory); category.setTextColor(RED); category.setOnClickListener(v->filterCategoryDialog()); filters.addView(category,new LinearLayout.LayoutParams(0,-2,1));
        if(filterStartDate!=null||!filterSources.isEmpty()||filterCategory!=null){Button clear=new Button(this);clear.setText("XÓA");clear.setTextColor(MUTED);clear.setOnClickListener(v->{filterStartDate=null;filterEndDate=null;filterCategory=null;filterSources.clear();draw();});filters.addView(clear,new LinearLayout.LayoutParams(-2,-2));}
        root.addView(filters,top(8));
        JSONArray items=store.items(); boolean found=false; String activeDate="";
        for(int i=items.length()-1;i>=0;i--) try{JSONObject o=items.getJSONObject(i);String date=o.getString("date");LocalDate d=LocalDate.parse(date);if(filterStartDate!=null&&d.isBefore(LocalDate.parse(filterStartDate)))continue;if(filterEndDate!=null&&d.isAfter(LocalDate.parse(filterEndDate)))continue;if(!filterSources.isEmpty()&&!filterSources.contains(o.optString("source","Tiền Mặt")))continue;if(filterCategory!=null&&!filterCategory.equals(o.optString("category","Chưa gắn thẻ")))continue;found=true;if(!date.equals(activeDate)){activeDate=date;root.addView(dayHeader(date,dayTotal(items,date)),top(16));}root.addView(expense(o),top(8));}catch(Exception ignored){}
        if(!found)root.addView(text("Chưa có khoản chi trong 6 tháng gần đây.",15,MUTED,false),top(14));
    }

    private View dayHeader(String isoDate,long total){LocalDate date=LocalDate.parse(isoDate);String label=date.equals(LocalDate.now())?"Hôm nay":date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM",new Locale("vi","VN")));LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(label,15,INK,true),new LinearLayout.LayoutParams(0,-2,1));row.addView(text("Tổng "+Format.money(total),14,MUTED,true));return row;}
    private long dayTotal(JSONArray items,String date){long total=0;for(int i=0;i<items.length();i++)try{JSONObject o=items.getJSONObject(i);if(date.equals(o.getString("date"))&&(filterSources.isEmpty()||filterSources.contains(o.optString("source","Tiền Mặt")))&&(filterCategory==null||filterCategory.equals(o.optString("category","Chưa gắn thẻ"))))total+=o.getLong("amount");}catch(Exception ignored){}return total;}
    private View expense(JSONObject o)throws Exception{LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(card(CARD,0xffe5e5e8));LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);words.addView(text(o.getString("note"),16,INK,true));LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.addView(chip(o.optString("category","Chưa gắn thẻ"),0xffeaf7ef,GREEN,v->editExpenseCategory(o)),new LinearLayout.LayoutParams(-2,dp(30)));meta.addView(chip(o.optString("source","Tiền Mặt"),0xfff1f1f1,MUTED,v->editExpenseSource(o)),new LinearLayout.LayoutParams(-2,dp(30)));meta.addView(text(" • "+(LocalDate.now().toString().equals(o.getString("date"))?"Hôm nay":o.getString("date")),13,MUTED,false),new LinearLayout.LayoutParams(-2,dp(30)));words.addView(meta);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("−"+Format.money(o.getLong("amount")),16,RED,true));row.setOnClickListener(v->expenseDialog(o));return row;}

    private TextView chip(String label,int fill,int color,View.OnClickListener listener){TextView v=text(label,12,color,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setSingleLine(true);v.setBackground(card(fill,0x00ffffff));v.setOnClickListener(listener);return v;}

    private View bottomNav(){LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(6),dp(4),dp(6),dp(4));nav.setBackground(card(CARD,0xffdddddf));nav.setElevation(dp(8));Button home=new Button(this);home.setText("⌂  HOME");home.setTextColor(tab==0?RED:MUTED);home.setOnClickListener(v->{tab=0;draw();});Button history=new Button(this);history.setText("▤  LỊCH SỬ");history.setTextColor(tab==1?RED:MUTED);history.setOnClickListener(v->{tab=1;draw();});nav.addView(home,new LinearLayout.LayoutParams(0,-1,1));nav.addView(history,new LinearLayout.LayoutParams(0,-1,1));return nav;}

    private void showDateFilter(){if(filterStartDate!=null&&filterEndDate==null)showEndDatePicker();else showStartDatePicker();}
    private void showStartDatePicker(){Calendar today=Calendar.getInstance();LocalDate selected=filterStartDate==null?LocalDate.now():LocalDate.parse(filterStartDate);DatePickerDialog dialog=new DatePickerDialog(this,(v,y,m,d)->{filterStartDate=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);filterEndDate=null;showEndDatePicker();},selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth());Calendar min=Calendar.getInstance();min.add(Calendar.MONTH,-5);min.set(Calendar.DAY_OF_MONTH,1);dialog.getDatePicker().setMinDate(min.getTimeInMillis());dialog.getDatePicker().setMaxDate(today.getTimeInMillis());dialog.show();}
    private void showEndDatePicker(){Calendar today=Calendar.getInstance();LocalDate start=LocalDate.parse(filterStartDate);DatePickerDialog dialog=new DatePickerDialog(this,(v,y,m,d)->{String picked=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);if(LocalDate.parse(picked).isBefore(start)){filterEndDate=filterStartDate;filterStartDate=picked;}else filterEndDate=picked;draw();},start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());Calendar min=Calendar.getInstance();min.add(Calendar.MONTH,-5);min.set(Calendar.DAY_OF_MONTH,1);dialog.getDatePicker().setMinDate(min.getTimeInMillis());dialog.getDatePicker().setMaxDate(today.getTimeInMillis());dialog.show();}
    private String dateFilterLabel(){if(filterStartDate==null)return "NGÀY: TẤT CẢ";if(filterEndDate==null)return "NGÀY: TỪ "+filterStartDate;return "NGÀY: "+filterStartDate+" → "+filterEndDate;}
    private void filterSourceDialog(){java.util.List<String> values=store.sources();boolean[] checked=new boolean[values.size()];for(int i=0;i<values.size();i++)checked[i]=filterSources.contains(values.get(i));new AlertDialog.Builder(this).setTitle("Lọc theo nguồn chi").setMultiChoiceItems(values.toArray(new String[0]),checked,(d,w,c)->checked[w]=c).setNegativeButton("Hủy",null).setPositiveButton("ÁP DỤNG",(d,w)->{filterSources.clear();for(int i=0;i<values.size();i++)if(checked[i])filterSources.add(values.get(i));draw();}).show();}
    private void filterCategoryDialog(){String[] values=new String[ExpenseParser.CATEGORIES.length+1];values[0]="Tất cả mục tiêu";System.arraycopy(ExpenseParser.CATEGORIES,0,values,1,ExpenseParser.CATEGORIES.length);int checked=filterCategory==null?0:Math.max(0,java.util.Arrays.asList(values).indexOf(filterCategory));new AlertDialog.Builder(this).setTitle("Lọc theo mục tiêu").setSingleChoiceItems(values,checked,(d,w)->{filterCategory=w==0?null:values[w];d.dismiss();draw();}).setNegativeButton("Hủy",null).show();}

    private void expenseDialog(JSONObject o){String stamp=o.optLong("time",0)>0?new SimpleDateFormat("HH:mm • dd/MM/yyyy",new Locale("vi","VN")).format(new java.util.Date(o.optLong("time"))):o.optString("date","");String message="Ghi chú: "+o.optString("note","")+"\nNội dung đã nhập: "+o.optString("raw",o.optString("note",""))+"\nSố tiền: "+Format.money(o.optLong("amount",0))+"\nMục tiêu: "+o.optString("category","Chưa gắn thẻ")+"\nNguồn tiền: "+o.optString("source","Tiền Mặt")+"\nThời gian: "+stamp;new AlertDialog.Builder(this).setTitle("Chi tiết khoản chi").setMessage(message).setNegativeButton("Đóng",null).setNeutralButton("ĐỔI NGUỒN",(d,w)->editExpenseSource(o)).setPositiveButton("ĐỔI MỤC TIÊU",(d,w)->editExpenseCategory(o)).show();}
    private void editExpenseCategory(JSONObject o){String current=o.optString("category","Chưa gắn thẻ");int checked=Math.max(0,java.util.Arrays.asList(ExpenseParser.CATEGORIES).indexOf(current));new AlertDialog.Builder(this).setTitle("Đổi mục tiêu chi tiêu").setSingleChoiceItems(ExpenseParser.CATEGORIES,checked,(d,w)->{String c=ExpenseParser.CATEGORIES[w];if(store.updateCategory(o,c)){NotificationHelper.refresh(this);Toast.makeText(this,"Đã chuyển sang "+c,Toast.LENGTH_SHORT).show();draw();}d.dismiss();}).setNegativeButton("Hủy",null).show();}
    private void editExpenseSource(JSONObject o){java.util.List<String> values=store.sources();int checked=Math.max(0,values.indexOf(o.optString("source","Tiền Mặt")));new AlertDialog.Builder(this).setTitle("Đổi nguồn chi").setSingleChoiceItems(values.toArray(new String[0]),checked,(d,w)->{String s=values.get(w);if(store.updateSource(o,s)){NotificationHelper.refresh(this);Toast.makeText(this,"Đã chuyển sang "+s,Toast.LENGTH_SHORT).show();draw();}d.dismiss();}).setNegativeButton("Hủy",null).show();}

    private void settingsDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);box.addView(text("Gemini API key",14,MUTED,true));EditText key=new EditText(this);key.setHint("Dán API key tại đây");key.setSingleLine(true);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(key,new LinearLayout.LayoutParams(-1,-2));box.addView(text("Giao diện",14,MUTED,true),top(16));RadioGroup group=new RadioGroup(this);String[] names={"Theo hệ thống","Sáng","Tối"};String current=getSharedPreferences("settings",MODE_PRIVATE).getString("theme","system");for(String name:names){RadioButton b=new RadioButton(this);b.setText(name);group.addView(b);if(("Theo hệ thống".equals(name)&&"system".equals(current))||("Sáng".equals(name)&&"light".equals(current))||("Tối".equals(name)&&"dark".equals(current)))b.setChecked(true);}box.addView(group);new AlertDialog.Builder(this).setTitle("Tùy chỉnh").setView(box).setNegativeButton("Hủy",null).setNeutralButton("XÓA API",(d,w)->{new SecretStore(this).setApiKey("");draw();}).setPositiveButton("LƯU",(d,w)->{String value=key.getText().toString().trim();if(!value.isEmpty())new SecretStore(this).setApiKey(value);int id=group.getCheckedRadioButtonId();RadioButton checked=group.findViewById(id);String theme=checked==null?"system":(checked.getText().toString().equals("Sáng")?"light":checked.getText().toString().equals("Tối")?"dark":"system");getSharedPreferences("settings",MODE_PRIVATE).edit().putString("theme",theme).apply();draw();}).show();}
    private void budgetDialog(){EditText e=new EditText(this);e.setHint("Ví dụ: 10.000.000");e.setInputType(2);e.setText(store.budget()==0?"":String.valueOf(store.budget()));e.setSelectAllOnFocus(true);int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));new AlertDialog.Builder(this).setTitle("Ngân sách tháng").setMessage("Tui sẽ chia số tiền còn lại đều cho các ngày còn lại.").setView(wrap).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{try{store.setBudget(Long.parseLong(e.getText().toString().replaceAll("\\D","")));NotificationHelper.refresh(this);draw();}catch(Exception x){Toast.makeText(this,"Số tiền chưa hợp lệ",Toast.LENGTH_SHORT).show();}}).show();}
    private LinearLayout stat(String label,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(label,12,MUTED,true));b.addView(text(value,18,INK,true));return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable card(int fill,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(14));g.setStroke(dp(1),stroke);return g;}
    private int darkGreen(){return Color.rgb(234,247,239);}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(px);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
