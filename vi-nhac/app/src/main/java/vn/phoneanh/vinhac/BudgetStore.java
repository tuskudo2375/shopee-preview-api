package titus.expenseassistant;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BudgetStore {
    static final String[] DEFAULT_CATEGORIES = {
            "Tiền chuyển đi", "Ăn uống", "Mua sắm", "Siêu thị", "Hóa đơn", "Giải trí",
            "Khách sạn", "Di chuyển", "Giáo dục", "Y tế", "Du lịch", "Chưa gắn thẻ"
    };
    static final String[] DEFAULT_SOURCES = {
            "Tiền Mặt", "Chuyển khoản", "Thẻ Tech", "Thẻ TP", "Thẻ VIB"
    };

    private static final String HISTORY_KEY = "expense_history_6m";
    private static final String SOURCES_KEY = "source_options_v2";
    private static final String CATEGORIES_KEY = "category_options_v2";
    private static final String EXCLUDED_DAYS_PREFIX = "excluded_days_";
    private final SharedPreferences prefs;

    BudgetStore(Context c) {
        prefs = c.getSharedPreferences("budget", Context.MODE_PRIVATE);
    }

    long budget() {
        return prefs.getLong(key("limit"), 0);
    }

    void setBudget(long value) {
        prefs.edit().putLong(key("limit"), value).apply();
    }

    void add(ExpenseParser.Result r, String raw) {
        add(r, raw, r.source.isEmpty() ? defaultSource() : r.source);
    }

    void add(ExpenseParser.Result r, String raw, String source) {
        try {
            JSONArray items = items();
            JSONObject item = new JSONObject();
            item.put("amount", r.amount);
            item.put("category", r.category);
            item.put("note", r.note);
            item.put("raw", raw);
            item.put("date", LocalDate.now().toString());
            item.put("time", System.currentTimeMillis());
            item.put("source", source == null || source.isEmpty() ? defaultSource() : source);
            items.put(item);
            prefs.edit().putString(HISTORY_KEY, items.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Adds one bank-notification transaction only once, even if the notification is reposted. */
    synchronized boolean addFromNotification(String eventId, String legacyId, ExpenseParser.Result r, String raw, String source, long time) {
        if (eventId == null || eventId.trim().isEmpty()) return false;
        try {
            JSONArray seen = new JSONArray(prefs.getString("notification_event_ids", "[]"));
            for (int i = 0; i < seen.length(); i++) {
                String id = seen.optString(i);
                if (eventId.equals(id) || legacyId.equals(id)) return false;
            }

            JSONArray items = items();
            for (int i = 0; i < items.length(); i++) {
                String id = items.optJSONObject(i).optString("notificationEventId");
                if (eventId.equals(id) || legacyId.equals(id)) return false;
            }
            LocalDate date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
            if (date.isBefore(YearMonth.now().minusMonths(5).atDay(1)) || date.isAfter(LocalDate.now())) return false;
            JSONObject item = new JSONObject();
            item.put("amount", r.amount);
            item.put("category", r.category);
            item.put("note", r.note);
            item.put("raw", raw);
            item.put("date", date.toString());
            item.put("time", time);
            item.put("source", source == null || source.isEmpty() ? defaultSource() : source);
            item.put("notificationEventId", eventId);
            item.put("needsNote", true);
            items.put(item);

            seen.put(eventId);
            while (seen.length() > 300) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < seen.length(); i++) trimmed.put(seen.optString(i));
                seen = trimmed;
            }
            prefs.edit().putString(HISTORY_KEY, items.toString())
                    .putString("notification_event_ids", seen.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean updateCategory(JSONObject target, String category) {
        return updateText(target, "category", category);
    }

    boolean updateSource(JSONObject target, String source) {
        return updateText(target, "source", source);
    }

    boolean updateNote(JSONObject target, String note) {
        return updateText(target, "note", note);
    }

    boolean deleteExpense(JSONObject target) {
        try {
            JSONArray all = items();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                // Match the complete row: edited timestamps may be shared by two expenses.
                boolean matches = item.length() == target.length();
                java.util.Iterator<String> keys = target.keys();
                while (matches && keys.hasNext()) {
                    String key = keys.next();
                    matches = item.has(key) && item.get(key).equals(target.get(key));
                }
                if (!matches) continue;
                all.remove(i);
                // Retain notification_event_ids so a repost does not recreate this expense.
                return prefs.edit().putString(HISTORY_KEY, all.toString()).commit();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean updateText(JSONObject target, String field, String value) {
        try {
            JSONArray all = items();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                if (sameItem(item, target)) {
                    item.put(field, value);
                    if ("note".equals(field)) item.put("needsNote", false);
                    prefs.edit().putString(HISTORY_KEY, all.toString()).apply();
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    boolean updateDateTime(JSONObject target, long millis) {
        try {
            JSONArray all = items();
            LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
            for (int i = 0; i < all.length(); i++) {
                JSONObject item = all.getJSONObject(i);
                if (sameItem(item, target)) {
                    item.put("time", millis);
                    item.put("date", date.toString());
                    prefs.edit().putString(HISTORY_KEY, all.toString()).apply();
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean sameItem(JSONObject item, JSONObject target) {
        long targetTime = target.optLong("time", -1);
        if (targetTime > 0) return item.optLong("time", -2) == targetTime;
        return item.optString("date").equals(target.optString("date"))
                && item.optLong("amount", -1) == target.optLong("amount", -2)
                && item.optString("raw").equals(target.optString("raw"));
    }

    JSONArray items() {
        JSONArray result = readHistoryOrMigrate();
        LocalDate cutoff = LocalDate.now().minusMonths(5).withDayOfMonth(1);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < result.length(); i++) {
            try {
                JSONObject item = result.getJSONObject(i);
                if (!LocalDate.parse(item.getString("date")).isBefore(cutoff)) kept.put(item);
            } catch (Exception ignored) {
            }
        }
        if (!kept.toString().equals(result.toString())) {
            prefs.edit().putString(HISTORY_KEY, kept.toString()).apply();
        }
        return kept;
    }

    long monthSpent() {
        return sum(YearMonth.now().toString());
    }

    long todaySpent() {
        return sum(LocalDate.now().toString());
    }

    private long sum(String date) {
        long total = 0;
        JSONArray all = items();
        for (int i = 0; i < all.length(); i++) {
            try {
                JSONObject item = all.getJSONObject(i);
                if (date == null || item.getString("date").equals(date)
                        || item.getString("date").startsWith(date)) {
                    total += item.getLong("amount");
                }
            } catch (Exception ignored) {
            }
        }
        return total;
    }

    long remaining() {
        return Math.max(0, budget() - monthSpent());
    }

    /** Calendar days which are still eligible for spending, including today when it is not off. */
    int daysRemainingInclusive() {
        return spendingDaysRemaining(LocalDate.now());
    }

    int spendingDaysRemaining(LocalDate from) {
        int count = 0;
        LocalDate end = YearMonth.from(from).atEndOfMonth();
        for (LocalDate date = from; !date.isAfter(end); date = date.plusDays(1)) {
            if (isSpendingDay(date)) count++;
        }
        return count;
    }

    long todayAllowance() {
        return allowanceFrom(LocalDate.now());
    }

    /**
     * Rebalances only the unspent budget over eligible days from the edit/current date
     * through the end of the current month. Earlier days are never reallocated.
     */
    long allowanceFrom(LocalDate from) {
        if (!isSpendingDay(from) || !YearMonth.from(from).equals(YearMonth.now())) return 0;
        return remaining() / Math.max(1, spendingDaysRemaining(from));
    }

    boolean isSpendingDay(LocalDate date) {
        return !excludedDays(YearMonth.from(date)).contains(date.toString());
    }

    int spendingDays(YearMonth month) {
        int count = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            if (isSpendingDay(month.atDay(day))) count++;
        }
        return count;
    }

    Set<String> excludedDays(YearMonth month) {
        Set<String> result = new LinkedHashSet<>();
        String saved = prefs.getString(EXCLUDED_DAYS_PREFIX + month, null);
        if (saved == null) return result;
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (!value.isEmpty() && value.startsWith(month.toString())) result.add(value);
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    void setExcludedDays(YearMonth month, Set<String> dates) {
        JSONArray array = new JSONArray();
        for (String date : dates) {
            if (date != null && date.startsWith(month.toString())) array.put(date);
        }
        prefs.edit().putString(EXCLUDED_DAYS_PREFIX + month, array.toString()).apply();
    }

    boolean hasSpendingCalendarConfig(YearMonth month) {
        return prefs.contains(EXCLUDED_DAYS_PREFIX + month);
    }

    boolean hasShownMonthlyPrompt(YearMonth month) {
        return prefs.getBoolean("spending_prompt_" + month, false);
    }

    void markMonthlyPromptShown(YearMonth month) {
        prefs.edit().putBoolean("spending_prompt_" + month, true).apply();
    }

    private String key(String suffix) {
        return YearMonth.now() + "_" + suffix;
    }

    String defaultSource() {
        String source = prefs.getString("default_source", DEFAULT_SOURCES[0]);
        if ("Tiền mặt".equals(source)) source = "Tiền Mặt";
        if ("Techcombank".equals(source) || "Thẻ Techcombank".equals(source)) source = "Thẻ Tech";
        List<String> available = sources();
        return available.contains(source) ? source : (available.isEmpty() ? DEFAULT_SOURCES[0] : available.get(0));
    }

    void setDefaultSource(String source) {
        if (source != null && sources().contains(source)) prefs.edit().putString("default_source", source).apply();
    }

    List<String> sources() {
        return readOptions(SOURCES_KEY, DEFAULT_SOURCES, "custom_sources");
    }

    List<String> categories() {
        return readOptions(CATEGORIES_KEY, DEFAULT_CATEGORIES, null);
    }

    void addSource(String source) {
        addOption(SOURCES_KEY, source, DEFAULT_SOURCES);
    }

    void addCategory(String category) {
        addOption(CATEGORIES_KEY, category, DEFAULT_CATEGORIES);
    }

    boolean renameSource(String oldValue, String newValue) {
        boolean changed = renameOption(SOURCES_KEY, oldValue, newValue, DEFAULT_SOURCES);
        if (changed && oldValue.equals(prefs.getString("default_source", ""))) {
            prefs.edit().putString("default_source", newValue).apply();
        }
        return changed;
    }

    boolean renameCategory(String oldValue, String newValue) {
        return renameOption(CATEGORIES_KEY, oldValue, newValue, DEFAULT_CATEGORIES);
    }

    boolean removeSource(String source) {
        List<String> values = sources();
        if (values.size() <= 1 || !values.remove(source)) return false;
        writeOptions(SOURCES_KEY, values);
        if (source.equals(prefs.getString("default_source", ""))) {
            prefs.edit().putString("default_source", values.get(0)).apply();
        }
        return true;
    }

    boolean removeCategory(String category) {
        List<String> values = categories();
        if (values.size() <= 1 || !values.remove(category)) return false;
        writeOptions(CATEGORIES_KEY, values);
        return true;
    }

    private boolean renameOption(String key, String oldValue, String newValue, String[] defaults) {
        String clean = newValue == null ? "" : newValue.trim();
        if (clean.isEmpty() || oldValue == null || oldValue.equals(clean)) return false;
        List<String> values = readOptions(key, defaults, null);
        if (!values.contains(oldValue) || containsIgnoreCase(values, clean)) return false;
        values.set(values.indexOf(oldValue), clean);
        writeOptions(key, values);
        return true;
    }

    private void addOption(String key, String value, String[] defaults) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty()) return;
        List<String> values = readOptions(key, defaults, null);
        if (containsIgnoreCase(values, clean)) return;
        values.add(clean);
        writeOptions(key, values);
    }

    private boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) if (value.equalsIgnoreCase(wanted)) return true;
        return false;
    }

    private List<String> readOptions(String key, String[] defaults, String legacyCustomKey) {
        List<String> result = new ArrayList<>();
        String saved = prefs.getString(key, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String value = array.optString(i, "").trim();
                    if (!value.isEmpty() && !containsIgnoreCase(result, value)) result.add(value);
                }
            } catch (Exception ignored) {
            }
        }
        if (result.isEmpty()) result.addAll(Arrays.asList(defaults));
        if (legacyCustomKey != null) {
            String legacy = prefs.getString(legacyCustomKey, "");
            if (!legacy.isEmpty()) {
                for (String value : legacy.split("\\|")) {
                    value = value.trim();
                    if (!value.isEmpty() && !containsIgnoreCase(result, value)) result.add(value);
                }
            }
        }
        writeOptions(key, result);
        return result;
    }

    private void writeOptions(String key, List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) array.put(value);
        prefs.edit().putString(key, array.toString()).apply();
    }

    /** Export money data and choices, intentionally excluding the Gemini API key. */
    JSONObject exportData() {
        JSONObject backup = new JSONObject();
        try {
            backup.put("format", "tro-ly-chi-tieu");
            backup.put("version", 1);
            backup.put("exportedAt", System.currentTimeMillis());
            backup.put("history", items());

            JSONArray sourceArray = new JSONArray();
            for (String value : sources()) sourceArray.put(value);
            backup.put("sources", sourceArray);

            JSONArray categoryArray = new JSONArray();
            for (String value : categories()) categoryArray.put(value);
            backup.put("categories", categoryArray);

            JSONObject budgets = new JSONObject();
            JSONObject excluded = new JSONObject();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.endsWith("_limit") && value instanceof Long) {
                    budgets.put(key.substring(0, key.length() - "_limit".length()), ((Long) value).longValue());
                } else if (key.startsWith(EXCLUDED_DAYS_PREFIX) && value instanceof String) {
                    String month = key.substring(EXCLUDED_DAYS_PREFIX.length());
                    excluded.put(month, new JSONArray((String) value));
                }
            }
            backup.put("budgets", budgets);
            backup.put("excludedDays", excluded);
            backup.put("defaultSource", prefs.getString("default_source", ""));
        } catch (Exception ignored) {
        }
        return backup;
    }

    /**
     * Import is additive: existing transactions and settings are never overwritten.
     * Returns the number of newly added transactions.
     */
    int importData(String json) throws Exception {
        JSONObject backup = new JSONObject(json);
        JSONArray imported = backup.optJSONArray("history");
        JSONArray all = items();
        int added = 0;
        if (imported != null) {
            for (int i = 0; i < imported.length(); i++) {
                JSONObject candidate = imported.optJSONObject(i);
                if (candidate == null || candidate.optLong("amount", 0) <= 0 || candidate.optString("date", "").isEmpty()) continue;
                boolean duplicate = false;
                for (int j = 0; j < all.length(); j++) {
                    if (sameItem(all.getJSONObject(j), candidate)) { duplicate = true; break; }
                }
                if (!duplicate) { all.put(new JSONObject(candidate.toString())); added++; }
            }
        }
        prefs.edit().putString(HISTORY_KEY, all.toString()).apply();

        mergeImportedOptions(SOURCES_KEY, DEFAULT_SOURCES, backup.optJSONArray("sources"));
        mergeImportedOptions(CATEGORIES_KEY, DEFAULT_CATEGORIES, backup.optJSONArray("categories"));

        JSONObject budgets = backup.optJSONObject("budgets");
        if (budgets != null) {
            JSONArray names = budgets.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String month = names.optString(i, "");
                if (!month.isEmpty() && !prefs.contains(month + "_limit")) {
                    prefs.edit().putLong(month + "_limit", budgets.optLong(month, 0)).apply();
                }
            }
        }

        JSONObject excluded = backup.optJSONObject("excludedDays");
        if (excluded != null) {
            JSONArray names = excluded.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String monthText = names.optString(i, "");
                try {
                    YearMonth month = YearMonth.parse(monthText);
                    Set<String> merged = excludedDays(month);
                    JSONArray dates = excluded.optJSONArray(monthText);
                    if (dates != null) for (int j = 0; j < dates.length(); j++) {
                        String date = dates.optString(j, "");
                        if (date.startsWith(monthText)) merged.add(date);
                    }
                    setExcludedDays(month, merged);
                } catch (Exception ignored) {
                }
            }
        }
        String defaultSource = backup.optString("defaultSource", "");
        if (!defaultSource.isEmpty() && !prefs.contains("default_source")) setDefaultSource(defaultSource);
        return added;
    }

    private void mergeImportedOptions(String key, String[] defaults, JSONArray imported) {
        List<String> merged = readOptions(key, defaults, null);
        if (imported != null) for (int i = 0; i < imported.length(); i++) {
            String value = imported.optString(i, "").trim();
            if (!value.isEmpty() && !containsIgnoreCase(merged, value)) merged.add(value);
        }
        writeOptions(key, merged);
    }

    private JSONArray readHistoryOrMigrate() {
        String saved = prefs.getString(HISTORY_KEY, null);
        if (saved != null) {
            try {
                return new JSONArray(saved);
            } catch (Exception ignored) {
            }
        }
        JSONArray merged = new JSONArray();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().endsWith("_items") && entry.getValue() instanceof String) {
                try {
                    JSONArray old = new JSONArray((String) entry.getValue());
                    for (int i = 0; i < old.length(); i++) merged.put(old.getJSONObject(i));
                } catch (Exception ignored) {
                }
            }
        }
        prefs.edit().putString(HISTORY_KEY, merged.toString()).apply();
        return merged;
    }
}
