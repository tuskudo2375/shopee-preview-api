package titus.expenseassistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;

final class BudgetStore {
    private final SharedPreferences prefs;
    BudgetStore(Context c) { prefs = c.getSharedPreferences("budget", Context.MODE_PRIVATE); }

    long budget() { return prefs.getLong(key("limit"), 0); }
    void setBudget(long value) { prefs.edit().putLong(key("limit"), value).apply(); }

    void add(ExpenseParser.Result r, String raw) {
        try {
            JSONArray items = items();
            JSONObject item = new JSONObject();
            item.put("amount", r.amount); item.put("category", r.category); item.put("note", r.note);
            item.put("raw", raw); item.put("date", LocalDate.now().toString()); item.put("time", System.currentTimeMillis());
            items.put(item);
            prefs.edit().putString(key("items"), items.toString()).apply();
        } catch (Exception ignored) {}
    }

    JSONArray items() { try { return new JSONArray(prefs.getString(key("items"), "[]")); } catch (Exception e) { return new JSONArray(); } }
    long monthSpent() { return sum(null); }
    long todaySpent() { return sum(LocalDate.now().toString()); }
    private long sum(String date) {
        long total = 0; JSONArray a = items();
        for (int i = 0; i < a.length(); i++) try { JSONObject o = a.getJSONObject(i); if (date == null || date.equals(o.getString("date"))) total += o.getLong("amount"); } catch (Exception ignored) {}
        return total;
    }
    long remaining() { return Math.max(0, budget() - monthSpent()); }
    int daysRemainingInclusive() { LocalDate d = LocalDate.now(); return YearMonth.from(d).lengthOfMonth() - d.getDayOfMonth() + 1; }
    long todayAllowance() { return remaining() / Math.max(1, daysRemainingInclusive()); }
    private String key(String suffix) { return YearMonth.now() + "_" + suffix; }
}
