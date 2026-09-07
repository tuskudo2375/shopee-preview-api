package titus.expenseassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private int RED, GREEN, INK, MUTED, PAGE, CARD;
    private BudgetStore store;
    private LinearLayout root;
    private int tab = 0;
    private String filterStartDate, filterEndDate, filterCategory;
    private final Set<String> filterSources = new LinkedHashSet<>();
    private int statsRange = 2; // 0 = 7 ngày, 1 = 30 ngày, 2 = tháng, 3 = tùy chọn
    private String statsStartDate, statsEndDate;
    private static final int EXPORT_REQUEST = 9001;
    private static final int IMPORT_REQUEST = 9002;
    private static final int[] PIE_COLORS = {0xffd92d20, 0xff2e8b57, 0xfff39c12, 0xff3f7cac, 0xff8e44ad, 0xff16a085, 0xffd35400, 0xff2c3e50, 0xffc0392b, 0xff27ae60, 0xff2980b9, 0xff7f8c7d};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new BudgetStore(this);
        updatePalette();
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        startForegroundService(new Intent(this, BudgetNotificationService.class));
        draw();
        if (store.budget() == 0) root.post(this::budgetDialog);
        else root.postDelayed(this::maybePromptSpendingDays, 350);
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener historyChanges = (prefs, key) -> {
        if ("expense_history_6m".equals(key) && root != null && !(getCurrentFocus() instanceof EditText)) {
            runOnUiThread(this::draw);
        }
    };

    @Override protected void onResume() {
        super.onResume();
        getSharedPreferences("budget", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(historyChanges);
        if (notificationListenerEnabled()) BankNotificationListenerService.rescan(this);
        if (root != null) { draw(); root.postDelayed(this::maybePromptSpendingDays, 350); }
    }

    @Override protected void onPause() {
        getSharedPreferences("budget", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(historyChanges);
        super.onPause();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == EXPORT_REQUEST) {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IllegalStateException("Không mở được file");
                    output.write(store.exportData().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "Đã xuất dữ liệu chi tiêu", Toast.LENGTH_SHORT).show();
            } else if (requestCode == IMPORT_REQUEST) {
                String json;
                try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalStateException("Không đọc được file");
                    byte[] chunk = new byte[8192]; int read;
                    while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
                    json = buffer.toString("UTF-8");
                }
                int count = store.importData(json);
                NotificationHelper.refresh(this);
                draw();
                Toast.makeText(this, "Đã nhập bổ sung " + count + " giao dịch, không ghi đè dữ liệu hiện tại", Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, "Không thể xử lý file dữ liệu", Toast.LENGTH_LONG).show();
        }
    }

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
        if (tab == 0) drawHome(scroll); else if (tab == 1) drawHistory(); else drawStats();
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
        YearMonth currentMonth = YearMonth.now();
        TextView spendingDays = text("Ngày chi tiêu: " + store.spendingDays(currentMonth) + "/" + currentMonth.lengthOfMonth() + "  •  Chạm để chọn ngày off", 14, MUTED, false);
        spendingDays.setPadding(0, 0, 0, dp(12));
        spendingDays.setOnClickListener(v -> spendingCalendarDialog(currentMonth));
        root.addView(spendingDays);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); int progress = store.budget() == 0 ? 0 : (int)Math.min(1000, store.monthSpent() * 1000 / store.budget()); bar.setProgress(progress); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(progress >= 1000 ? RED : GREEN)); root.addView(bar, new LinearLayout.LayoutParams(-1,dp(9)));
        LinearLayout stats = new LinearLayout(this); stats.setOrientation(LinearLayout.HORIZONTAL); stats.setPadding(0,dp(18),0,dp(18)); stats.addView(stat("ĐÃ CHI THÁNG", Format.money(store.monthSpent())), new LinearLayout.LayoutParams(0,-2,1)); stats.addView(stat("HÔM NAY", Format.money(store.todaySpent())), new LinearLayout.LayoutParams(0,-2,1)); root.addView(stats);
        boolean over = store.todaySpent() > store.todayAllowance() && store.todaySpent() > 0; TextView allowance = text((over ? "⚠ Vượt mức gợi ý hôm nay\n" : "Mức có thể chi hôm nay\n") + Format.money(store.todayAllowance()) + "\n" + store.daysRemainingInclusive() + " ngày chi tiêu còn lại", 18, over ? RED : GREEN, true); allowance.setPadding(dp(16),dp(15),dp(16),dp(15)); allowance.setBackground(card(over ? 0xffffeeee : (darkGreen()), over ? RED : GREEN)); root.addView(allowance);
        root.addView(text("Nhập nhanh", 22, INK, true), top(26));
        EditText input = new EditText(this); input.setHint("Ví dụ: ăn trưa 30k • thẻ Tech"); input.setTextSize(18); input.setSingleLine(true); input.setPadding(dp(16),dp(14),dp(16),dp(14)); input.setBackground(card(CARD, 0xffdddddf)); root.addView(input, top(10));
        input.setOnFocusChangeListener((v, focused) -> { if (focused) { Runnable reveal = () -> { Rect rect = new Rect(); input.getDrawingRect(rect); scroll.offsetDescendantRectToMyCoords(input, rect); rect.bottom += dp(180); scroll.requestChildRectangleOnScreen(input, rect, true); }; input.postDelayed(reveal, 350); input.postDelayed(reveal, 900); } });
        TextView preview = text("Gemini sẽ tự phân loại mục tiêu và nguồn tiền", 14, MUTED, false); root.addView(preview, top(8));
        Button add = new Button(this); add.setText("THÊM CHI TIÊU"); add.setTextColor(Color.WHITE); add.setTypeface(Typeface.DEFAULT_BOLD); add.setBackground(card(RED, RED)); root.addView(add, top(12));
        input.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){ ExpenseParser.Result p=ExpenseParser.parse(s.toString(), store.categories(), store.sources()); preview.setText(p.amount>0 ? p.category+" • "+(p.source.isEmpty()?store.defaultSource():p.source)+" • "+Format.money(p.amount) : "Gemini sẽ tự phân loại mục tiêu và nguồn tiền"); } public void afterTextChanged(Editable e){} });
        SecretStore secrets = new SecretStore(this);
        add.setOnClickListener(v -> { String raw=input.getText().toString(); ExpenseParser.Result p=ExpenseParser.parse(raw, store.categories(), store.sources()); if(p.amount<=0){input.setError("Nhập thêm số tiền, ví dụ 30k");return;} String fallbackSource=p.source.isEmpty()?store.defaultSource():p.source; add.setEnabled(false); add.setText(secrets.hasApiKey()?"GEMINI ĐANG PHÂN LOẠI…":"ĐANG LƯU…"); GeminiClassifier.classifyAsync(this,raw,p.category,fallbackSource,store.categories(),store.sources(),(result,usedAi)->{ExpenseParser.Result smart=new ExpenseParser.Result(p.amount,result.category,p.note,result.source);store.add(smart,raw,result.source);NotificationHelper.refresh(this);Toast.makeText(this,"Đã thêm vào "+result.category+" • "+result.source,Toast.LENGTH_SHORT).show();draw();}); });
        drawToday();
    }

    private void drawToday() {
        root.addView(text("Giao dịch hôm nay", 22, INK, true), top(28)); JSONArray items = store.items(); String today = LocalDate.now().toString(); boolean found = false;
        for (int i=items.length()-1;i>=0;i--) try { JSONObject o=items.getJSONObject(i); if(today.equals(o.getString("date"))){found=true; root.addView(expense(o), top(8));} } catch(Exception ignored){}
        if(!found) root.addView(text("Chưa có khoản chi hôm nay.",15,MUTED,false),top(12));
    }

    private void drawStats() {
        root.addView(text("Thống kê chi tiêu", 22, INK, true), top(22));
        root.addView(text("Biểu đồ phân bổ theo danh mục", 14, MUTED, false), top(4));

        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {"7 NGÀY", "30 NGÀY", "THÁNG", "TÙY CHỌN"};
        for (int i = 0; i < names.length; i++) {
            final int selected = i;
            Button button = new Button(this);
            button.setText(names[i]);
            button.setTextSize(11);
            button.setTextColor(statsRange == i ? RED : MUTED);
            button.setOnClickListener(v -> {
                if (selected == 3) showStatsCustomStartPicker();
                else { statsRange = selected; draw(); }
            });
            rangeRow.addView(button, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        root.addView(rangeRow, top(8));

        LocalDate[] dates = statsDates();
        if (dates == null) {
            root.addView(text("Chọn ngày bắt đầu và ngày kết thúc để xem thống kê.", 15, MUTED, false), top(20));
            return;
        }

        root.addView(text("Từ " + shortDate(dates[0]) + " đến " + shortDate(dates[1]), 14, MUTED, false), top(2));
        LinkedHashMap<String, Long> totals = new LinkedHashMap<>();
        for (String category : store.categories()) totals.put(category, 0L);
        JSONArray items = store.items();
        long total = 0;
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                LocalDate date = LocalDate.parse(item.optString("date"));
                if (date.isBefore(dates[0]) || date.isAfter(dates[1])) continue;
                String category = item.optString("category", "Chưa gắn thẻ");
                long amount = item.optLong("amount", 0);
                totals.put(category, totals.containsKey(category) ? totals.get(category) + amount : amount);
                total += amount;
            } catch (Exception ignored) {}
        }

        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            if (entry.getValue() > 0) { labels.add(entry.getKey()); values.add(entry.getValue()); }
        }
        if (total == 0) {
            TextView empty = text("Chưa có giao dịch trong khoảng thời gian này.", 16, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(42), dp(10), dp(42));
            empty.setBackground(card(CARD, 0xffe5e5e8));
            root.addView(empty, top(16));
            return;
        }

        TextView selection = text("Chạm vào một phần biểu đồ để xem chi tiết", 14, MUTED, false);
        selection.setGravity(Gravity.CENTER);
        PieChartView chart = new PieChartView(this, labels, values, total);
        chart.setMinimumHeight(dp(280));
        chart.setBackground(card(CARD, 0xffe5e5e8));
        final long chartTotal = total;
        chart.setCategoryListener((label, amount) -> selection.setText(label + " • " + Format.money(amount) + " • " + percent(amount, chartTotal)));
        root.addView(chart, top(14));
        root.addView(selection, top(8));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setPadding(0, dp(14), 0, dp(6));
        summary.addView(stat("TỔNG CHI", Format.money(total)), new LinearLayout.LayoutParams(0, -2, 1));
        summary.addView(stat("SỐ KHOẢN", String.valueOf(values.size())), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(summary);

        for (int i = 0; i < labels.size(); i++) {
            LinearLayout legend = new LinearLayout(this);
            legend.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            dot.setBackgroundColor(PIE_COLORS[i % PIE_COLORS.length]);
            legend.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
            long amount = values.get(i);
            TextView label = text("  " + labels.get(i), 14, INK, true);
            legend.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            legend.addView(text(Format.money(amount) + "  " + percent(amount, total), 14, MUTED, false));
            root.addView(legend, top(10));
        }
    }

    private LocalDate[] statsDates() {
        LocalDate today = LocalDate.now();
        if (statsRange == 0) return new LocalDate[]{today.minusDays(6), today};
        if (statsRange == 1) return new LocalDate[]{today.minusDays(29), today};
        if (statsRange == 2) return new LocalDate[]{today.withDayOfMonth(1), today};
        if (statsStartDate == null || statsEndDate == null) return null;
        return new LocalDate[]{LocalDate.parse(statsStartDate), LocalDate.parse(statsEndDate)};
    }

    private String shortDate(LocalDate date) { return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)); }
    private String percent(long amount, long total) { return String.format(Locale.ROOT, "%.1f%%", total == 0 ? 0 : amount * 100d / total); }

    private void showStatsCustomStartPicker() {
        LocalDate today = LocalDate.now();
        LocalDate selected = statsStartDate == null ? today.minusDays(6) : LocalDate.parse(statsStartDate);
        DatePickerDialog dialog = new DatePickerDialog(this, (v, y, m, d) -> {
            statsStartDate = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d);
            statsEndDate = null;
            showStatsCustomEndPicker();
        }, selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
        Calendar min = Calendar.getInstance(); min.add(Calendar.MONTH, -5); min.set(Calendar.DAY_OF_MONTH, 1);
        dialog.getDatePicker().setMinDate(min.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showStatsCustomEndPicker() {
        LocalDate start = LocalDate.parse(statsStartDate);
        LocalDate today = LocalDate.now();
        DatePickerDialog dialog = new DatePickerDialog(this, (v, y, m, d) -> {
            String picked = String.format(Locale.ROOT, "%04d-%02d-%02d", y, m + 1, d);
            if (LocalDate.parse(picked).isBefore(start)) { statsEndDate = statsStartDate; statsStartDate = picked; }
            else statsEndDate = picked;
            statsRange = 3;
            draw();
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
        dialog.getDatePicker().setMinDate(start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
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
    private View expense(JSONObject o)throws Exception{
        boolean needsNote=o.optBoolean("needsNote",false);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(12),dp(14),dp(12));row.setBackground(card(needsNote?0xffffeeee:CARD,needsNote?RED:0xffe5e5e8));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text((needsNote?"⚠ ":"")+o.getString("note"),16,needsNote?RED:INK,true));
        if(needsNote) words.addView(text("⚠ CHƯA GHI CHÚ",11,RED,true),top(3));
        LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.addView(chip(o.optString("category","Chưa gắn thẻ"),0xffeaf7ef,GREEN,v->editExpenseCategory(o)),new LinearLayout.LayoutParams(-2,dp(30)));meta.addView(chip(o.optString("source","Tiền Mặt"),0xfff1f1f1,MUTED,v->editExpenseSource(o)),new LinearLayout.LayoutParams(-2,dp(30)));words.addView(meta);
        TextView date=text("• "+(LocalDate.now().toString().equals(o.getString("date"))?"Hôm nay":o.getString("date")),13,MUTED,false);date.setPadding(dp(6),0,0,0);words.addView(date,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(text("−"+Format.money(o.getLong("amount")),16,RED,true));row.setOnClickListener(v->expenseDialog(o));return row;
    }

    private TextView chip(String label,int fill,int color,View.OnClickListener listener){TextView v=text(label,12,color,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setSingleLine(true);v.setBackground(card(fill,0x00ffffff));v.setOnClickListener(listener);return v;}

    private View bottomNav(){LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(4),dp(4),dp(4),dp(4));nav.setBackground(card(CARD,0xffdddddf));nav.setElevation(dp(8));Button home=new Button(this);home.setText("⌂  HOME");home.setTextSize(11);home.setTextColor(tab==0?RED:MUTED);home.setOnClickListener(v->{tab=0;draw();});Button history=new Button(this);history.setText("▤  LỊCH SỬ");history.setTextSize(11);history.setTextColor(tab==1?RED:MUTED);history.setOnClickListener(v->{tab=1;draw();});Button stats=new Button(this);stats.setText("◔  THỐNG KÊ");stats.setTextSize(11);stats.setTextColor(tab==2?RED:MUTED);stats.setOnClickListener(v->{tab=2;draw();});nav.addView(home,new LinearLayout.LayoutParams(0,-1,1));nav.addView(history,new LinearLayout.LayoutParams(0,-1,1));nav.addView(stats,new LinearLayout.LayoutParams(0,-1,1));return nav;}

    private void showDateFilter(){if(filterStartDate!=null&&filterEndDate==null)showEndDatePicker();else showStartDatePicker();}
    private void showStartDatePicker(){Calendar today=Calendar.getInstance();LocalDate selected=filterStartDate==null?LocalDate.now():LocalDate.parse(filterStartDate);DatePickerDialog dialog=new DatePickerDialog(this,(v,y,m,d)->{filterStartDate=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);filterEndDate=null;showEndDatePicker();},selected.getYear(),selected.getMonthValue()-1,selected.getDayOfMonth());Calendar min=Calendar.getInstance();min.add(Calendar.MONTH,-5);min.set(Calendar.DAY_OF_MONTH,1);dialog.getDatePicker().setMinDate(min.getTimeInMillis());dialog.getDatePicker().setMaxDate(today.getTimeInMillis());dialog.show();}
    private void showEndDatePicker(){Calendar today=Calendar.getInstance();LocalDate start=LocalDate.parse(filterStartDate);DatePickerDialog dialog=new DatePickerDialog(this,(v,y,m,d)->{String picked=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);if(LocalDate.parse(picked).isBefore(start)){filterEndDate=filterStartDate;filterStartDate=picked;}else filterEndDate=picked;draw();},start.getYear(),start.getMonthValue()-1,start.getDayOfMonth());Calendar min=Calendar.getInstance();min.add(Calendar.MONTH,-5);min.set(Calendar.DAY_OF_MONTH,1);dialog.getDatePicker().setMinDate(min.getTimeInMillis());dialog.getDatePicker().setMaxDate(today.getTimeInMillis());dialog.show();}
    private String dateFilterLabel(){if(filterStartDate==null)return "NGÀY: TẤT CẢ";if(filterEndDate==null)return "NGÀY: TỪ "+filterStartDate;return "NGÀY: "+filterStartDate+" → "+filterEndDate;}
    private void filterSourceDialog(){java.util.List<String> values=new ArrayList<>(store.sources());JSONArray oldItems=store.items();for(int i=0;i<oldItems.length();i++)try{String value=oldItems.getJSONObject(i).optString("source","");if(!value.isEmpty()&&!values.contains(value))values.add(value);}catch(Exception ignored){}boolean[] checked=new boolean[values.size()];for(int i=0;i<values.size();i++)checked[i]=filterSources.contains(values.get(i));new AlertDialog.Builder(this).setTitle("Lọc theo nguồn chi").setMultiChoiceItems(values.toArray(new String[0]),checked,(d,w,c)->checked[w]=c).setNegativeButton("Hủy",null).setPositiveButton("ÁP DỤNG",(d,w)->{filterSources.clear();for(int i=0;i<values.size();i++)if(checked[i])filterSources.add(values.get(i));draw();}).show();}
    private void filterCategoryDialog(){List<String> options=new ArrayList<>(store.categories());JSONArray oldItems=store.items();for(int i=0;i<oldItems.length();i++)try{String value=oldItems.getJSONObject(i).optString("category","");if(!value.isEmpty()&&!options.contains(value))options.add(value);}catch(Exception ignored){}String[] values=new String[options.size()+1];values[0]="Tất cả mục tiêu";for(int i=0;i<options.size();i++)values[i+1]=options.get(i);int checked=filterCategory==null?0:Math.max(0,java.util.Arrays.asList(values).indexOf(filterCategory));new AlertDialog.Builder(this).setTitle("Lọc theo mục tiêu").setSingleChoiceItems(values,checked,(d,w)->{filterCategory=w==0?null:values[w];d.dismiss();draw();}).setNegativeButton("Hủy",null).show();}

    private void expenseDialog(JSONObject o){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),0,dp(18),dp(8));
        TextView amountLabel=text("Số tiền giao dịch",14,MUTED,false); amountLabel.setGravity(Gravity.CENTER); box.addView(amountLabel,top(4));
        TextView amount=text("−"+Format.money(o.optLong("amount",0)),28,INK,true); amount.setGravity(Gravity.CENTER); amount.setPadding(0,dp(8),0,dp(12)); amount.setBackground(card(CARD,0xffe5e5e8)); box.addView(amount,top(6));
        LinearLayout details=new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(14),dp(8),dp(14),dp(8)); details.setBackground(card(CARD,0xffe5e5e8));
        TextView noteValue=text(o.optString("note",""),15,INK,true); noteValue.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); noteValue.setMaxLines(2);
        LinearLayout noteRow=detailRow("Ghi chú",noteValue); details.addView(noteRow); noteRow.setOnClickListener(v->editExpenseNote(o,null,noteValue));
        TextView categoryChip=chip(o.optString("category","Chưa gắn thẻ"),0xffeaf7ef,GREEN,v->editExpenseCategory(o)); details.addView(detailRow("Danh mục",categoryChip));
        TextView sourceChip=chip(o.optString("source","Tiền Mặt"),0xfff1f1f1,MUTED,v->editExpenseSource(o)); details.addView(detailRow("Nguồn chi",sourceChip));
        String stamp=o.optLong("time",0)>0?new SimpleDateFormat("HH:mm • dd/MM/yyyy",new Locale("vi","VN")).format(new java.util.Date(o.optLong("time"))):o.optString("date",""); TextView dateValue=text(stamp,14,INK,true); LinearLayout dateRow=detailRow("Ngày & giờ",dateValue); details.addView(dateRow); box.addView(details,top(12));
        AlertDialog detail=new AlertDialog.Builder(this).setTitle("Chi tiết giao dịch").setView(box).setNegativeButton("Đóng",null).setNeutralButton("Xóa giao dịch",null).create();
        noteRow.setOnClickListener(v->editExpenseNote(o,detail,noteValue)); categoryChip.setOnClickListener(v->editExpenseCategory(o,detail)); sourceChip.setOnClickListener(v->editExpenseSource(o,detail)); dateRow.setOnClickListener(v->editExpenseDateTime(o,detail,dateValue)); detail.show();
        detail.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(RED);
        detail.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
            new AlertDialog.Builder(this).setTitle("Xóa giao dịch này?")
                .setMessage(o.optString("note", "") + "\n" + Format.money(o.optLong("amount", 0))
                    + "\n" + o.optString("date", "") + "\nChỉ xóa khoản chi này khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (confirmation, which) -> {
                    if (store.deleteExpense(o)) {
                        detail.dismiss();
                        draw();
                        NotificationHelper.refresh(this);
                        Toast.makeText(this, "Đã xóa giao dịch", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Chưa xóa được. Mở lại giao dịch và thử lại.", Toast.LENGTH_LONG).show();
                    }
                }).show());
    }
    private LinearLayout detailRow(String label,View value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(7),0,dp(7));row.addView(text(label,14,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));row.addView(value,new LinearLayout.LayoutParams(-2,-2));return row;}
    private void editExpenseCategory(JSONObject o){editExpenseCategory(o,null);}
    private void editExpenseCategory(JSONObject o,Dialog parent){String current=o.optString("category","Chưa gắn thẻ");List<String> values=new ArrayList<>(store.categories());if(!values.contains(current))values.add(0,current);int checked=Math.max(0,values.indexOf(current));new AlertDialog.Builder(this).setTitle("Đổi danh mục chi tiêu").setSingleChoiceItems(values.toArray(new String[0]),checked,(d,w)->{String c=values.get(w);if(store.updateCategory(o,c)){try{o.put("category",c);}catch(Exception ignored){}NotificationHelper.refresh(this);Toast.makeText(this,"Đã chuyển sang "+c,Toast.LENGTH_SHORT).show();if(parent!=null)parent.dismiss();draw();}d.dismiss();}).setNegativeButton("Hủy",null).show();}
    private void editExpenseSource(JSONObject o){editExpenseSource(o,null);}
    private void editExpenseSource(JSONObject o,Dialog parent){java.util.List<String> values=new ArrayList<>(store.sources());String current=o.optString("source","Tiền Mặt");if(!values.contains(current))values.add(0,current);int checked=Math.max(0,values.indexOf(current));new AlertDialog.Builder(this).setTitle("Đổi nguồn chi").setSingleChoiceItems(values.toArray(new String[0]),checked,(d,w)->{String s=values.get(w);if(store.updateSource(o,s)){try{o.put("source",s);}catch(Exception ignored){}NotificationHelper.refresh(this);Toast.makeText(this,"Đã chuyển sang "+s,Toast.LENGTH_SHORT).show();if(parent!=null)parent.dismiss();draw();}d.dismiss();}).setNegativeButton("Hủy",null).show();}
    private void editExpenseNote(JSONObject o,Dialog parent,TextView valueView){EditText input=new EditText(this);input.setText(o.optString("note",""));input.setSelectAllOnFocus(true);input.setSingleLine(false);input.setMaxLines(3);input.setHint("Nhập ghi chú");new AlertDialog.Builder(this).setTitle("Sửa ghi chú").setView(input).setNegativeButton("Hủy",null).setPositiveButton("Lưu",(d,w)->{String note=input.getText().toString().trim();if(note.isEmpty())note="Không có ghi chú";if(store.updateNote(o,note)){try{o.put("note",note);}catch(Exception ignored){}if(valueView!=null)valueView.setText(note);Toast.makeText(this,"Đã lưu ghi chú",Toast.LENGTH_SHORT).show();if(parent!=null){parent.dismiss();draw();}else draw();}}).show();}
    private void editExpenseDateTime(JSONObject o,Dialog parent,TextView valueView){Calendar initial=Calendar.getInstance();if(o.optLong("time",0)>0)initial.setTimeInMillis(o.optLong("time"));else try{LocalDate d=LocalDate.parse(o.optString("date",LocalDate.now().toString()));initial.set(d.getYear(),d.getMonthValue()-1,d.getDayOfMonth());}catch(Exception ignored){}DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->{Calendar selected=(Calendar)initial.clone();selected.set(Calendar.YEAR,y);selected.set(Calendar.MONTH,m);selected.set(Calendar.DAY_OF_MONTH,d);TimePickerDialog timePicker=new TimePickerDialog(this,(tv,hour,minute)->{selected.set(Calendar.HOUR_OF_DAY,hour);selected.set(Calendar.MINUTE,minute);selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);if(store.updateDateTime(o,selected.getTimeInMillis())){try{o.put("time",selected.getTimeInMillis());o.put("date",new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(selected.getTime()));}catch(Exception ignored){}if(valueView!=null)valueView.setText(new SimpleDateFormat("HH:mm • dd/MM/yyyy",new Locale("vi","VN")).format(selected.getTime()));Toast.makeText(this,"Đã cập nhật ngày giờ",Toast.LENGTH_SHORT).show();if(parent!=null){parent.dismiss();draw();}else draw();}},initial.get(Calendar.HOUR_OF_DAY),initial.get(Calendar.MINUTE),true);timePicker.show();},initial.get(Calendar.YEAR),initial.get(Calendar.MONTH),initial.get(Calendar.DAY_OF_MONTH));Calendar min=Calendar.getInstance();min.add(Calendar.MONTH,-5);min.set(Calendar.DAY_OF_MONTH,1);picker.getDatePicker().setMinDate(min.getTimeInMillis());picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();}

    private void settingsDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),0,dp(20),0);
        box.addView(text("Gemini API key",14,MUTED,true));
        EditText key=new EditText(this);key.setHint("Dán API key tại đây");key.setSingleLine(true);key.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(key,new LinearLayout.LayoutParams(-1,-2));
        box.addView(text("Giao diện",14,MUTED,true),top(16));
        RadioGroup group=new RadioGroup(this);String[] names={"Theo hệ thống","Sáng","Tối"};String current=getSharedPreferences("settings",MODE_PRIVATE).getString("theme","system");
        for(String name:names){RadioButton b=new RadioButton(this);b.setText(name);group.addView(b);if(("Theo hệ thống".equals(name)&&"system".equals(current))||("Sáng".equals(name)&&"light".equals(current))||("Tối".equals(name)&&"dark".equals(current)))b.setChecked(true);}box.addView(group);
        box.addView(text("Dữ liệu dùng cho các giao dịch mới",14,MUTED,true),top(14));
        Button calendarButton=new Button(this);calendarButton.setText("LỊCH NGÀY CHI TIÊU / NGÀY OFF");box.addView(calendarButton,top(4));
        Button sourceButton=new Button(this);sourceButton.setText("QUẢN LÝ NGUỒN TIỀN");box.addView(sourceButton,top(2));
        Button categoryButton=new Button(this);categoryButton.setText("QUẢN LÝ DANH MỤC");box.addView(categoryButton,top(2));
        box.addView(text("Tự động lấy giao dịch từ thông báo ngân hàng",14,MUTED,true),top(14));
        Button notificationButton=new Button(this);notificationButton.setText(notificationListenerEnabled()?"ĐÃ BẬT ĐỌC THÔNG BÁO":"BẬT ĐỌC THÔNG BÁO NGÂN HÀNG");box.addView(notificationButton,top(3));
        TextView listenerStatus=text(listenerStatusText(),12,MUTED,false);box.addView(listenerStatus,top(3));
        Button scanButton=new Button(this);scanButton.setText("QUÉT LẠI THÔNG BÁO ĐANG CÓ");box.addView(scanButton,top(3));
        scanButton.setOnClickListener(v->{
            if(!notificationListenerEnabled()) {Toast.makeText(this,"Hãy bật quyền đọc thông báo trước",Toast.LENGTH_LONG).show();return;}
            String result=BankNotificationListenerService.rescan(this);
            listenerStatus.setText(listenerStatusText());
            Toast.makeText(this,result,Toast.LENGTH_LONG).show();
        });
        box.addView(text("Chỉ tạo khoản chi khi thông báo có dấu hiệu trừ tiền. Android sẽ hiển thị các app có thể cấp quyền đọc thông báo; ní chỉ bật khi thấy phù hợp.",12,MUTED,false),top(2));
        box.addView(text("Sao lưu dữ liệu để đổi APK không sợ mất lịch sử",14,MUTED,true),top(14));
        Button exportButton=new Button(this);exportButton.setText("XUẤT DỮ LIỆU RA FILE");box.addView(exportButton,top(3));
        Button importButton=new Button(this);importButton.setText("NHẬP DỮ LIỆU TỪ FILE");box.addView(importButton,top(2));
        ScrollView settingsScroll=new ScrollView(this);
        settingsScroll.setFillViewport(true);
        settingsScroll.setVerticalScrollBarEnabled(true);
        settingsScroll.addView(box,new FrameLayout.LayoutParams(-1,-2));
        int contentHeight=Math.round(getResources().getDisplayMetrics().heightPixels*.58f);
        settingsScroll.setLayoutParams(new LinearLayout.LayoutParams(-1,contentHeight));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Tùy chỉnh").setView(settingsScroll).setNegativeButton("Hủy",null).setNeutralButton("XÓA API",(d,w)->{new SecretStore(this).setApiKey("");draw();}).setPositiveButton("LƯU",(d,w)->{String value=key.getText().toString().trim();if(!value.isEmpty())new SecretStore(this).setApiKey(value);int id=group.getCheckedRadioButtonId();RadioButton checked=group.findViewById(id);String theme=checked==null?"system":(checked.getText().toString().equals("Sáng")?"light":checked.getText().toString().equals("Tối")?"dark":"system");getSharedPreferences("settings",MODE_PRIVATE).edit().putString("theme",theme).apply();draw();}).create();
        calendarButton.setOnClickListener(v->{dialog.dismiss();spendingCalendarDialog(YearMonth.now());});
        sourceButton.setOnClickListener(v->{dialog.dismiss();optionManagerDialog(true);});
        categoryButton.setOnClickListener(v->{dialog.dismiss();optionManagerDialog(false);});
        notificationButton.setOnClickListener(v->{dialog.dismiss();try{startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}catch(Exception ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}});
        exportButton.setOnClickListener(v->{dialog.dismiss();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"tro-ly-chi-tieu-backup.json");startActivityForResult(intent,EXPORT_REQUEST);});
        importButton.setOnClickListener(v->{dialog.dismiss();Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");startActivityForResult(intent,IMPORT_REQUEST);});
        dialog.show();
    }

    private boolean notificationListenerEnabled(){
        android.app.NotificationManager manager=getSystemService(android.app.NotificationManager.class);
        android.content.ComponentName component=new android.content.ComponentName(this,BankNotificationListenerService.class);
        if(Build.VERSION.SDK_INT>=27)return manager.isNotificationListenerAccessGranted(component);
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(enabled==null)return false;
        for(String value:enabled.split(":"))if(component.equals(android.content.ComponentName.unflattenFromString(value)))return true;
        return false;
    }

    private String listenerStatusText(){
        if(!notificationListenerEnabled())return "Chưa cấp quyền đọc thông báo";
        return (BankNotificationListenerService.isConnected()?"Đang kết nối đọc thông báo":"Đã cấp quyền nhưng chưa kết nối — thử tắt/bật lại quyền")
                + "\n" + BankNotificationListenerService.scanStatus();
    }

    private void budgetDialog(){
        EditText e=new EditText(this);e.setHint("Ví dụ: 10.000.000");e.setInputType(2);e.setText(store.budget()==0?"":String.valueOf(store.budget()));e.setSelectAllOnFocus(true);
        int p=dp(20);LinearLayout wrap=new LinearLayout(this);wrap.setPadding(p,0,p,0);wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("Ngân sách tháng").setMessage("Tui sẽ chia số tiền còn lại cho các ngày chi tiêu, sau khi trừ những ngày off ní chọn.").setView(wrap).setNegativeButton("Hủy",null).setNeutralButton("LỊCH NGÀY",(d,w)->spendingCalendarDialog(YearMonth.now())).setPositiveButton("Lưu",(d,w)->{try{store.setBudget(Long.parseLong(e.getText().toString().replaceAll("\\D","")));NotificationHelper.refresh(this);draw();root.postDelayed(this::maybePromptSpendingDays,250);}catch(Exception x){Toast.makeText(this,"Số tiền chưa hợp lệ",Toast.LENGTH_SHORT).show();}}).show();
    }

    private void maybePromptSpendingDays(){
        LocalDate today=LocalDate.now();YearMonth month=YearMonth.from(today);
        if(store.budget()<=0||store.hasSpendingCalendarConfig(month)||store.hasShownMonthlyPrompt(month))return;
        store.markMonthlyPromptShown(month);
        new AlertDialog.Builder(this).setTitle("Thiết lập ngày chi tiêu tháng "+month.getMonthValue()+"/"+month.getYear())
                .setMessage("Ní chạm chọn những ngày off ở nhà. Ngân sách sẽ chỉ chia cho các ngày chi tiêu còn lại.")
                .setNegativeButton("ĐỂ SAU",null)
                .setPositiveButton("CHỌN NGÀY OFF",(d,w)->spendingCalendarDialog(month)).show();
    }

    private void spendingCalendarDialog(YearMonth initialMonth){
        final Dialog dialog=new Dialog(this);dialog.setTitle("Lịch ngày chi tiêu");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(10),dp(14),dp(10));
        TextView hint=text("Chạm từng ngày để đánh dấu OFF. Cấu hình này chỉ ảnh hưởng cách chia ngân sách, không sửa giao dịch cũ.",13,MUTED,false);hint.setPadding(dp(4),0,dp(4),dp(8));box.addView(hint);
        LinearLayout calendar=new LinearLayout(this);calendar.setOrientation(LinearLayout.VERTICAL);box.addView(calendar,new LinearLayout.LayoutParams(-1,-2));
        Button done=new Button(this);done.setText("XONG");box.addView(done,top(6));
        final YearMonth[] shown={initialMonth};
        final Runnable[] render={null};
        render[0]=()->{
            calendar.removeAllViews();YearMonth month=shown[0];Set<String> excluded=new LinkedHashSet<>(store.excludedDays(month));
            LinearLayout monthBar=new LinearLayout(this);monthBar.setGravity(Gravity.CENTER_VERTICAL);
            Button previous=new Button(this);previous.setText("‹");previous.setTextSize(24);monthBar.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView monthTitle=text("Tháng "+month.getMonthValue()+"/"+month.getYear(),17,INK,true);monthTitle.setGravity(Gravity.CENTER);monthBar.addView(monthTitle,new LinearLayout.LayoutParams(0,dp(48),1));
            Button next=new Button(this);next.setText("›");next.setTextSize(24);monthBar.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));calendar.addView(monthBar);
            previous.setOnClickListener(v->{shown[0]=shown[0].minusMonths(1);render[0].run();});next.setOnClickListener(v->{shown[0]=shown[0].plusMonths(1);render[0].run();});
            LinearLayout weekdays=new LinearLayout(this);String[] names={"CN","T2","T3","T4","T5","T6","T7"};for(String name:names){TextView d=text(name,12,MUTED,true);d.setGravity(Gravity.CENTER);weekdays.addView(d,new LinearLayout.LayoutParams(0,dp(28),1));}calendar.addView(weekdays);
            int leading=month.atDay(1).getDayOfWeek().getValue()%7;
            for(int week=0;week<6;week++){
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);row.setPadding(0,dp(1),0,dp(1));
                for(int column=0;column<7;column++){
                    int day=week*7+column-leading+1;TextView cell=text("",15,INK,false);cell.setGravity(Gravity.CENTER);cell.setMinHeight(dp(40));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(cell,lp);
                    if(day<1||day>month.lengthOfMonth())continue;
                    LocalDate date=month.atDay(day);String iso=date.toString();boolean off=excluded.contains(iso);cell.setText(String.valueOf(day));
                    int fill=off?0xffffe7e5:(date.equals(LocalDate.now())?0xffeaf7ef:CARD);int stroke=off?RED:(date.equals(LocalDate.now())?GREEN:0xffe5e5e8);cell.setTextColor(off?RED:INK);cell.setTypeface(Typeface.DEFAULT,off?Typeface.BOLD:Typeface.NORMAL);cell.setBackground(card(fill,stroke));cell.setOnClickListener(v->{Set<String> selected=new LinkedHashSet<>(store.excludedDays(month));if(selected.contains(iso))selected.remove(iso);else selected.add(iso);store.setExcludedDays(month,selected);NotificationHelper.refresh(this);render[0].run();});
                }
                calendar.addView(row);
            }
            calendar.addView(text("Đã chọn off: "+excluded.size()+" ngày  •  Ngày chi tiêu: "+store.spendingDays(month)+"/"+month.lengthOfMonth(),13,MUTED,true),top(6));
        };
        done.setOnClickListener(v->{store.setExcludedDays(shown[0],store.excludedDays(shown[0]));NotificationHelper.refresh(this);dialog.dismiss();draw();});
        dialog.setContentView(box);dialog.show();render[0].run();
        if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94),-2);
    }

    private void optionManagerDialog(boolean sourceList){
        final Dialog dialog=new Dialog(this);dialog.setTitle(sourceList?"Quản lý nguồn tiền":"Quản lý danh mục");
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(10),dp(16),dp(10));
        TextView hint=text("Thêm, đổi tên hoặc xóa lựa chọn cho giao dịch mới. Giao dịch đã lưu không bị đổi theo.",13,MUTED,false);hint.setPadding(0,0,0,dp(8));box.addView(hint);
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);ScrollView listScroll=new ScrollView(this);listScroll.addView(list);box.addView(listScroll,new LinearLayout.LayoutParams(-1,dp(360)));
        LinearLayout addRow=new LinearLayout(this);addRow.setGravity(Gravity.CENTER_VERTICAL);EditText addInput=new EditText(this);addInput.setSingleLine(true);addInput.setHint(sourceList?"Nguồn mới":"Danh mục mới");addRow.addView(addInput,new LinearLayout.LayoutParams(0,-2,1));Button add=new Button(this);add.setText("THÊM");addRow.addView(add,new LinearLayout.LayoutParams(-2,-2));box.addView(addRow,top(8));
        Button close=new Button(this);close.setText("XONG");box.addView(close,top(4));
        final Runnable[] render={null};
        render[0]=()->{list.removeAllViews();List<String> values=sourceList?store.sources():store.categories();for(String value:values){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(value,15,INK,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button edit=new Button(this);edit.setText("SỬA");row.addView(edit,new LinearLayout.LayoutParams(-2,-2));Button remove=new Button(this);remove.setText("XÓA");row.addView(remove,new LinearLayout.LayoutParams(-2,-2));edit.setOnClickListener(v->editOptionDialog(sourceList,value,render[0]));remove.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Xóa "+value+"?").setMessage("Chỉ xóa khỏi danh sách lựa chọn mới; lịch sử cũ vẫn giữ nguyên.").setNegativeButton("HỦY",null).setPositiveButton("XÓA",(d,w)->{boolean ok=sourceList?store.removeSource(value):store.removeCategory(value);if(!ok)Toast.makeText(this,"Phải còn ít nhất một lựa chọn",Toast.LENGTH_SHORT).show();render[0].run();}).show());list.addView(row,new LinearLayout.LayoutParams(-1,-2));}};
        add.setOnClickListener(v->{String value=addInput.getText().toString().trim();if(value.isEmpty())return;if(sourceList)store.addSource(value);else store.addCategory(value);addInput.setText("");render[0].run();});close.setOnClickListener(v->{dialog.dismiss();draw();});
        dialog.setContentView(box);dialog.show();render[0].run();if(dialog.getWindow()!=null)dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.96),-2);
    }

    private void editOptionDialog(boolean sourceList,String oldValue,Runnable refresh){EditText input=new EditText(this);input.setSingleLine(true);input.setText(oldValue);input.setSelectAllOnFocus(true);new AlertDialog.Builder(this).setTitle("Đổi tên").setView(input).setNegativeButton("HỦY",null).setPositiveButton("LƯU",(d,w)->{String value=input.getText().toString().trim();boolean ok=sourceList?store.renameSource(oldValue,value):store.renameCategory(oldValue,value);if(!ok)Toast.makeText(this,"Tên trống hoặc bị trùng",Toast.LENGTH_SHORT).show();else refresh.run();}).show();}

    private final class PieChartView extends View {
        final int[] COLORS = PIE_COLORS;
        private final List<String> labels;
        private final List<Long> values;
        private final long total;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private CategoryListener listener;

        PieChartView(android.content.Context context, List<String> labels, List<Long> values, long total) {
            super(context); this.labels = labels; this.values = values; this.total = total; setClickable(true);
        }

        void setCategoryListener(CategoryListener listener) { this.listener = listener; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * .34f;
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            float start = -90f;
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < values.size(); i++) {
                float sweep = values.get(i) * 360f / total;
                paint.setColor(COLORS[i % COLORS.length]);
                canvas.drawArc(oval, start, sweep, true, paint);
                start += sweep;
            }
            paint.setColor(PAGE);
            canvas.drawCircle(cx, cy, radius * .58f, paint);
            paint.setColor(INK);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(dp(16));
            canvas.drawText(Format.money(total), cx, cy + dp(5), paint);
            paint.setTextSize(dp(11));
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("TỔNG CHI", cx, cy + dp(23), paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() != MotionEvent.ACTION_UP) return true;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float dx = event.getX() - cx, dy = event.getY() - cy;
            float radius = Math.min(getWidth(), getHeight()) * .34f;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            if (distance < radius * .58f || distance > radius) return true;
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90d;
            if (angle < 0) angle += 360d;
            double cursor = 0;
            for (int i = 0; i < values.size(); i++) {
                cursor += values.get(i) * 360d / total;
                if (angle <= cursor) {
                    if (listener != null) listener.onSelected(labels.get(i), values.get(i));
                    break;
                }
            }
            return true;
        }
    }

    private interface CategoryListener { void onSelected(String label, long amount); }

    private LinearLayout stat(String label,String value){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.addView(text(label,12,MUTED,true));b.addView(text(value,18,INK,true));return b;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable card(int fill,int stroke){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(14));g.setStroke(dp(1),stroke);return g;}
    private int darkGreen(){return Color.rgb(234,247,239);}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(px);return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
