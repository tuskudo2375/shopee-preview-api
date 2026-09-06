package titus.expenseassistant;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExpenseParser {
    // Kept for compatibility with older code and old saved data. New UI reads options from BudgetStore.
    static final String[] CATEGORIES = BudgetStore.DEFAULT_CATEGORIES;
    static final String[] SOURCES = BudgetStore.DEFAULT_SOURCES;

    static final class Result {
        final long amount;
        final String category;
        final String note;
        final String source;

        Result(long amount, String category, String note) {
            this(amount, category, note, "");
        }

        Result(long amount, String category, String note, String source) {
            this.amount = amount;
            this.category = category;
            this.note = note;
            this.source = source;
        }
    }

    private static final Pattern MONEY = Pattern.compile("(?i)(\\d+(?:[.,]\\d{1,3})*)\\s*(k|nghin|ngan|tr|trieu|m)?");

    static Result parse(String raw) {
        return parse(raw, Arrays.asList(CATEGORIES), Arrays.asList(SOURCES));
    }

    static Result parse(String raw, List<String> categories, List<String> sources) {
        String safe = raw == null ? "" : raw;
        String normalized = plain(safe.toLowerCase(Locale.ROOT));
        Matcher matcher = MONEY.matcher(normalized);
        long amount = 0;
        int start = -1;
        int end = -1;
        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2);
            double value;
            if (("tr".equals(unit) || "trieu".equals(unit) || "m".equals(unit))
                    && number.matches("\\d+[.,]\\d{1,2}")) {
                value = Double.parseDouble(number.replace(',', '.'));
            } else if (number.matches(".*[.,]\\d{3}(?:[.,]\\d{3})*$")) {
                value = Double.parseDouble(number.replaceAll("[.,]", ""));
            } else {
                value = Double.parseDouble(number.replace(',', '.'));
            }
            long multiplier = unit == null ? (value < 1000 ? 1000 : 1)
                    : (("tr".equals(unit) || "trieu".equals(unit) || "m".equals(unit)) ? 1_000_000 : 1_000);
            amount = Math.round(value * multiplier);
            start = matcher.start();
            end = matcher.end();
        }

        String category = categoryFor(normalized, categories);
        String note = safe.trim();
        if (start >= 0) {
            note = (safe.substring(0, Math.min(start, safe.length()))
                    + safe.substring(Math.min(end, safe.length()))).trim();
        }
        return new Result(amount, category, note.isEmpty() ? category : note, sourceFor(normalized, sources));
    }

    static String explicitSource(String raw) {
        return sourceFor(plain(raw == null ? "" : raw.toLowerCase(Locale.ROOT)), Arrays.asList(SOURCES));
    }

    static String explicitSource(String raw, List<String> sources) {
        return sourceFor(plain(raw == null ? "" : raw.toLowerCase(Locale.ROOT)), sources);
    }

    private static String sourceFor(String s, List<String> sources) {
        // A user-created label wins when it is explicitly written in the quick note.
        for (String option : sources) {
            String normalizedOption = plain(option.toLowerCase(Locale.ROOT));
            if (normalizedOption.length() >= 3 && s.contains(normalizedOption)) return option;
        }

        String semantic = "";
        if (has(s, "the tech", "techcombank", "tcb") || token(s, "tech")) semantic = "Thẻ Tech";
        else if (has(s, "the tp", "tpbank", "tp bank") || token(s, "tp")) semantic = "Thẻ TP";
        else if (has(s, "the vib") || token(s, "vib")) semantic = "Thẻ VIB";
        else if (has(s, "chuyen khoan", "banking", "transfer") || token(s, "bank") || token(s, "ck")) semantic = "Chuyển khoản";
        else if (has(s, "tien mat", "cash") || token(s, "tm")) semantic = "Tiền Mặt";
        if (semantic.isEmpty()) return "";
        return resolveSource(semantic, sources);
    }

    private static String resolveSource(String semantic, List<String> sources) {
        for (String option : sources) if (plain(option.toLowerCase(Locale.ROOT)).equals(plain(semantic.toLowerCase(Locale.ROOT)))) return option;
        for (String option : sources) {
            String value = plain(option.toLowerCase(Locale.ROOT));
            if ("thẻ tech".equals(semantic) && has(value, "the tech", "techcombank", "tcb")) return option;
            if ("thẻ tp".equals(semantic) && has(value, "the tp", "tpbank", "tp bank")) return option;
            if ("thẻ vib".equals(semantic) && has(value, "the vib", "vib")) return option;
            if ("chuyển khoản".equals(semantic) && has(value, "chuyen khoan", "bank", "ck")) return option;
            if ("tiền mặt".equals(semantic) && has(value, "tien mat", "cash")) return option;
        }
        return "";
    }

    private static String categoryFor(String s, List<String> categories) {
        // This makes custom labels usable immediately when the user types their exact name.
        for (String option : categories) {
            String value = plain(option.toLowerCase(Locale.ROOT));
            if (value.length() >= 4 && s.contains(value)) return option;
        }

        String semantic = "";
        if (has(s, "chuyen tien", "gui tien", "tien chuyen di")) semantic = "Tiền chuyển đi";
        else if (has(s, "an sang", "an trua", "an toi", "an vat", "tra sua", "cafe", "ca phe", "ca he", "nuoc ngot", "banh", "com", "pho", "bun", "hu tieu", "thuc an", "an uong")) semantic = "Ăn uống";
        else if (has(s, "sieu thi", "winmart", "coopmart", "bach hoa", "grocery")) semantic = "Siêu thị";
        else if (has(s, "tien dien", "tien nuoc", "wifi", "internet", "dien thoai", "tien nha", "hoa don")) semantic = "Hóa đơn";
        else if (has(s, "mua sam", "mua do", "quan ao", "giay", "my pham", "shopee")) semantic = "Mua sắm";
        else if (has(s, "phim", "game", "karaoke", "giai tri")) semantic = "Giải trí";
        else if (has(s, "khach san", "hotel", "resort")) semantic = "Khách sạn";
        else if (has(s, "xang", "grab", "taxi", "gui xe", "xe buyt", "di lai", "di chuyen")) semantic = "Di chuyển";
        else if (has(s, "hoc phi", "truong", "sach", "khoa hoc", "giao duc")) semantic = "Giáo dục";
        else if (has(s, "thuoc", "kham", "benh vien", "suc khoe", "y te")) semantic = "Y tế";
        else if (has(s, "du lich", "may bay", "ve tau", "tour")) semantic = "Du lịch";
        if (!semantic.isEmpty()) {
            String resolved = resolveCategory(semantic, categories);
            if (!resolved.isEmpty()) return resolved;
        }
        for (String option : categories) if ("chua gan the".equals(plain(option.toLowerCase(Locale.ROOT)))) return option;
        return categories.isEmpty() ? "Chưa gắn thẻ" : categories.get(categories.size() - 1);
    }

    private static String resolveCategory(String semantic, List<String> categories) {
        String normalizedSemantic = plain(semantic.toLowerCase(Locale.ROOT));
        for (String option : categories) if (plain(option.toLowerCase(Locale.ROOT)).equals(normalizedSemantic)) return option;
        if ("an uong".equals(normalizedSemantic)) {
            for (String option : categories) {
                String value = plain(option.toLowerCase(Locale.ROOT));
                if (value.contains("an") && value.contains("uong")) return option;
            }
        }
        return "";
    }

    private static boolean has(String s, String... words) {
        for (String word : words) if (s.contains(word)) return true;
        return false;
    }

    private static boolean token(String s, String word) {
        return (" " + s + " ").contains(" " + word + " ");
    }

    static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd');
    }
}
