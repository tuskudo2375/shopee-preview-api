package vn.phoneanh.vinhac;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExpenseParser {
    static final class Result {
        final long amount;
        final String category;
        final String note;
        Result(long amount, String category, String note) { this.amount = amount; this.category = category; this.note = note; }
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
        return new Result(amount, category, note.isEmpty() ? category : note);
    }

    private static String categoryFor(String s) {
        if (has(s, "tra sua", "an vat", "cafe", "ca phe", "nuoc ngot", "banh", "snack")) return "Ăn vặt";
        if (has(s, "an sang", "an trua", "an toi", "com", "pho", "bun", "hu tieu", "thuc an")) return "Nhu cầu";
        if (has(s, "xang", "grab", "taxi", "gui xe", "xe buyt", "di lai")) return "Đi lại";
        if (has(s, "dien", "nuoc", "wifi", "internet", "dien thoai", "tien nha", "hoa don")) return "Hóa đơn";
        if (has(s, "mua sam", "quan ao", "giay", "my pham", "shopee")) return "Mua sắm";
        if (has(s, "thuoc", "kham", "benh vien", "suc khoe")) return "Sức khỏe";
        if (has(s, "phim", "game", "du lich", "giai tri")) return "Giải trí";
        return "Khác";
    }

    private static boolean has(String s, String... words) { for (String w : words) if (s.contains(w)) return true; return false; }
    private static String plain(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ', 'd'); }
}
