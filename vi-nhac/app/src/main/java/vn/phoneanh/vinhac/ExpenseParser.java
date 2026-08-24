package titus.expenseassistant;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExpenseParser {
    static final String[] CATEGORIES = {"Tiền chuyển đi", "Ăn uống", "Mua sắm", "Siêu thị", "Hóa đơn", "Giải trí", "Khách sạn", "Di chuyển", "Giáo dục", "Y tế", "Du lịch", "Chưa gắn thẻ"};
    static final String[] SOURCES = {"Tiền Mặt", "Chuyển khoản", "Thẻ Tech", "Thẻ TP", "Thẻ VIB"};

    static final class Result {
        final long amount;
        final String category;
        final String note;
        final String source;
        Result(long amount, String category, String note) { this(amount, category, note, ""); }
        Result(long amount, String category, String note, String source) { this.amount = amount; this.category = category; this.note = note; this.source = source; }
    }

    private static final Pattern MONEY = Pattern.compile("(?i)(\\d+(?:[.,]\\d{1,3})*)\\s*(k|nghin|ngan|tr|trieu|m)?");

    static Result parse(String raw) {
        String normalized = plain(raw.toLowerCase(Locale.ROOT));
        Matcher matcher = MONEY.matcher(normalized);
        long amount = 0;
        int start = -1, end = -1;
        while (matcher.find()) {
            String number = matcher.group(1);
            String unit = matcher.group(2);
            double value;
            if (("tr".equals(unit) || "trieu".equals(unit) || "m".equals(unit)) && number.matches("\\d+[.,]\\d{1,2}"))
                value = Double.parseDouble(number.replace(',', '.'));
            else if (number.matches(".*[.,]\\d{3}(?:[.,]\\d{3})*$"))
                value = Double.parseDouble(number.replaceAll("[.,]", ""));
            else value = Double.parseDouble(number.replace(',', '.'));
            long multiplier = unit == null ? (value < 1000 ? 1000 : 1) :
                    (unit.equals("tr") || unit.equals("trieu") || unit.equals("m") ? 1_000_000 : 1_000);
            amount = Math.round(value * multiplier);
            start = matcher.start(); end = matcher.end();
        }
        String category = categoryFor(normalized);
        String note = raw.trim();
        if (start >= 0) note = (raw.substring(0, Math.min(start, raw.length())) + raw.substring(Math.min(end, raw.length()))).trim();
        return new Result(amount, category, note.isEmpty() ? category : note, sourceFor(normalized));
    }

    static String explicitSource(String raw) {
        return sourceFor(plain(raw == null ? "" : raw.toLowerCase(Locale.ROOT)));
    }

    private static String sourceFor(String s) {
        if (has(s, "the tech", "techcombank", "tcb")) return "Thẻ Tech";
        if (has(s, "the tp", "tpbank", "tp bank")) return "Thẻ TP";
        if (has(s, "the vib", "vib")) return "Thẻ VIB";
        if (has(s, "chuyen khoan", "banking", "ck")) return "Chuyển khoản";
        if (has(s, "tien mat", "cash")) return "Tiền Mặt";
        return "";
    }

    private static String categoryFor(String s) {
        if (has(s, "chuyen tien", "gui tien", "tien chuyen di")) return "Tiền chuyển đi";
        if (has(s, "an sang", "an trua", "an toi", "an vat", "tra sua", "cafe", "ca phe", "nuoc ngot", "banh", "com", "pho", "bun", "hu tieu", "thuc an", "an uong")) return "Ăn uống";
        if (has(s, "sieu thi", "winmart", "coopmart", "bach hoa", "go grocery")) return "Siêu thị";
        if (has(s, "dien", "nuoc", "wifi", "internet", "dien thoai", "tien nha", "hoa don")) return "Hóa đơn";
        if (has(s, "mua sam", "mua do", "quan ao", "giay", "my pham", "shopee")) return "Mua sắm";
        if (has(s, "phim", "game", "karaoke", "giai tri")) return "Giải trí";
        if (has(s, "khach san", "hotel", "resort")) return "Khách sạn";
        if (has(s, "xang", "grab", "taxi", "gui xe", "xe buyt", "di lai", "di chuyen")) return "Di chuyển";
        if (has(s, "hoc phi", "truong", "sach", "khoa hoc", "giao duc")) return "Giáo dục";
        if (has(s, "thuoc", "kham", "benh vien", "suc khoe", "y te")) return "Y tế";
        if (has(s, "du lich", "may bay", "ve tau", "tour")) return "Du lịch";
        return "Chưa gắn thẻ";
    }

    private static boolean has(String s, String... words) { for (String w : words) if (s.contains(w)) return true; return false; }
    private static String plain(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd'); }
}
