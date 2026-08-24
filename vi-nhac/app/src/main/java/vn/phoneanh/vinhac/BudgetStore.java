package titus.expenseassistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

final class BudgetStore {
    private static final String HISTORY_KEY = "expense_history_6m";
    private final SharedPreferences prefs;
    BudgetStore(Context c) { prefs = c.getSharedPreferences("budget", Context.MODE_PRIVATE); }

    long budget() { return prefs.getLong(key("limit"), 0); }
    void setBudget(long value) { prefs.edit().putLong(key("limit"), value).apply(); }

    void add(ExpenseParser.Result r, String raw) {
        add(r, raw, r.source.isEmpty() ? defaultSource() : r.source);
    }

    void add(ExpenseParser.Result r, String raw, String source) {
        try {
            JSONArray items = items();
            JSONObject item = new JSONObject();
            item.put("amount", r.amount); item.put("category", r.category); item.put("note", r.note);
            item.put("raw", raw); item.put("date", LocalDate.now().toString()); item.put("time", System.currentTimeMillis());
            item.put("source", source == null || source.isEmpty() ? defaultSource() : source);
            items.put(item);
            prefs.edit().putString(HISTORY_KEY, items.toString()).apply();
        } catch (Exception ignored) {}
    }

    boolean updateCategory(JSONObject target, String category) {
        try {
            JSONArray all = items();
            long targetTime = target.optLong("time", -1);
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                boolean same = targetTime > 0 && item.optLong("time", -2) == targetTime;
                if (!same && targetTime <= 0) same = item.optString("date").equals(target.optString("date")) && item.optLong("amount", -1) == target.optLong("amount", -2) && item.optString("raw").equals(target.optString("raw"));
                if (same) {
                    item.put("category", category);
                    prefs.edit().putString(HISTORY_KEY, all.toString()).apply();
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    JSONArray items() {
        JSONArray result = readHistoryOrMigrate();
        LocalDate cutoff = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < result.length(); i++) try {
            JSONObject item = result.getJSONObject(i);
            if (!LocalDate.parse(item.getString("date")).isBefore(cutoff)) kept.put(item);
        } catch (Exception ignored) {}
        if (!kept.toString().equals(result.toString())) prefs.edit().putString(HISTORY_KEY, kept.toString()).apply();
        return kept;
    }
    long monthSpent() { return sum(YearMonth.now().toString()); }
    long todaySpent() { return sum(LocalDate.now().toString()); }
    private long sum(String date) {
        long total = 0; JSONArray a = items();
        for (int i = 0; i < a.length(); i++) try { JSONObject o = a.getJSONObject(i); if (date == null || o.getString("date").equals(date) || o.getString("date").startsWith(date)) total += o.getLong("amount"); } catch (Exception ignored) {}
        return total;
    }
    long remaining() { return Math.max(0, budget() - monthSpent()); }
    int daysRemainingInclusive() { LocalDate d = LocalDate.now(); return YearMonth.from(d).lengthOfMonth() - d.getDayOfMonth() + 1; }
    long todayAllowance() { return remaining() / Math.max(1, daysRemainingInclusive()); }
    private String key(String suffix) { return YearMonth.now() + "_" + suffix; }

    String defaultSource() { return prefs.getString("default_source", "Tiền mặt"); }
    void setDefaultSource(String source) { prefs.edit().putString("default_source", source).apply(); }
    List<String> sources() {
        ArrayList<String> values = new ArrayList<>(); values.add("Tiền mặt"); values.add("Techcombank"); values.add("Thẻ Techcombank");
        String custom = prefs.getString("custom_sources", "");
        for (String item : custom.split("\\|")) if (!item.trim().isEmpty() && !values.contains(item.trim())) values.add(item.trim());
        return values;
    }
    void addSource(String source) {
        String clean = source.trim(); if (clean.isEmpty() || sources().contains(clean)) return;
        String old = prefs.getString("custom_sources", ""); prefs.edit().putString("custom_sources", old.isEmpty() ? clean : old + "|" + clean).apply();
    }

    private JSONArray readHistoryOrMigrate() {
        String saved = prefs.getString(HISTORY_KEY, null);
        if (saved != null) try { return new JSONArray(saved); } catch (Exception ignored) {}
        JSONArray merged = new JSONArray();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) if (entry.getKey().endsWith("_items") && entry.getValue() instanceof String) {
            try { JSONArray old = new JSONArray((String) entry.getValue()); for (int i=0; i<old.length(); i++) merged.put(old.getJSONObject(i)); } catch (Exception ignored) {}
        }
        prefs.edit().putString(HISTORY_KEY, merged.toString()).apply();
        return merged;
    }
}
