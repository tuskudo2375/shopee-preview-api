package titus.expenseassistant;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only likely debit notifications; incoming/refund notifications are ignored. */
final class NotificationExpenseParser {
    private static final Pattern MONEY = Pattern.compile("(?i)(-?\\s*\\d[\\d.,]{0,18})\\s*(vnd|vnđ|₫|đ|dong|k|nghin|ngàn|ngan)?");

    private NotificationExpenseParser() {}

    static ExpenseParser.Result parse(String title, String body, String packageName,
                                      List<String> categories, List<String> sources) {
        String safeTitle = title == null ? "" : title.trim();
        String safeBody = body == null ? "" : body.trim();
        String combined = (safeTitle + " " + safeBody).trim();
        String plain = ExpenseParser.plain(combined.toLowerCase(Locale.ROOT));
        if (combined.isEmpty() || isIncoming(plain) || !isDebit(plain)) return null;

        Candidate best = findAmount(combined, plain);
        if (best == null || best.amount <= 0) return null;

        String source = findSource(combined, packageName, sources);
        // Do not silently classify an unknown bank notification as cash.
        if (source.isEmpty()) return null;

        ExpenseParser.Result classified = ExpenseParser.parse(combined, categories, sources);
        String note = compact(safeTitle.isEmpty() ? safeBody : safeTitle + " • " + safeBody);
        return new ExpenseParser.Result(best.amount, classified.category, note, source);
    }

    private static Candidate findAmount(String raw, String plain) {
        Matcher matcher = MONEY.matcher(raw);
        Candidate best = null;
        while (matcher.find()) {
            String number = matcher.group(1).replace(" ", "");
            String unit = matcher.group(2) == null ? "" : ExpenseParser.plain(matcher.group(2).toLowerCase(Locale.ROOT));
            String before = ExpenseParser.plain(raw.substring(Math.max(0, matcher.start() - 70), matcher.start()).toLowerCase(Locale.ROOT));
            String after = ExpenseParser.plain(raw.substring(matcher.end(), Math.min(raw.length(), matcher.end() + 30)).toLowerCase(Locale.ROOT));
            String context = before + " " + after;
            boolean negative = number.startsWith("-");
            if (number.startsWith("-")) number = number.substring(1);
            long amount = parseNumber(number, unit, !unit.isEmpty());
            if (amount <= 0) continue;
            boolean hasCurrency = !unit.isEmpty();
            boolean hasDebitContext = has(context, "so tien", "gia tri", "thanh toan", "payment", "amount", "tru", "trich", "debit", "chi tieu", "mua hang", "da su dung");
            if (!hasCurrency && !negative && !hasDebitContext) continue;

            int score = 0;
            if (hasCurrency) score += 4;
            if (negative) score += 5;
            if (hasDebitContext) score += 6;
            if (has(before, "so du", "balance", "con lai", "han muc", "available")) score -= 9;
            if (has(after, "so du", "balance", "con lai", "han muc", "available")) score -= 6;
            if (amount > 2_000_000_000L) score -= 4;
            Candidate candidate = new Candidate(amount, score);
            if (best == null || candidate.score > best.score) best = candidate;
        }
        return best;
    }

    private static long parseNumber(String raw, String unit, boolean hasUnit) {
        String number = raw.replace(" ", "");
        try {
            double value;
            if (number.matches("\\d+[.,]\\d{3}(?:[.,]\\d{3})*")) value = Double.parseDouble(number.replaceAll("[.,]", ""));
            else value = Double.parseDouble(number.replace(',', '.'));
            if ("k".equals(unit) || "nghin".equals(unit) || "ngan".equals(unit) || "ngan".equals(unit)) value *= 1000;
            else if ("tr".equals(unit) || "trieu".equals(unit)) value *= 1_000_000;
            else if (!hasUnit && value < 1000) value *= 1000;
            return Math.round(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String findSource(String raw, String packageName, List<String> sources) {
        String all = ExpenseParser.plain(((packageName == null ? "" : packageName) + " " + raw).toLowerCase(Locale.ROOT));
        String explicit = ExpenseParser.explicitSource(all, sources);
        if (!explicit.isEmpty()) return explicit;
        if (has(all, "techcombank", "tcb", "the tech")) return ExpenseParser.explicitSource("tech", sources);
        if (has(all, "tpbank", "tp bank", "the tp")) return ExpenseParser.explicitSource("tp", sources);
        if (has(all, "vib", "the vib")) return ExpenseParser.explicitSource("vib", sources);
        if (has(all, "tai khoan", " tk ", "chuyen khoan", "banking", "transfer", "trich no tai khoan")) return ExpenseParser.explicitSource("bank", sources);
        return "";
    }

    private static boolean isDebit(String text) {
        return has(text, "tru", "trich no", "thanh toan", "chi tieu", "mua hang", "debit", "payment", "da su dung", "giao dich thanh cong", "transaction")
                || text.contains("-");
    }

    private static boolean isIncoming(String text) {
        return has(text, "nhan tien", "ghi co", "cong tien", "hoan tien", "refund", "cashback", "nap tien", "incoming", "received", "chuyen den");
    }

    private static boolean has(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private static String compact(String value) {
        String result = value.replaceAll("\\s+", " ").trim();
        return result.length() > 180 ? result.substring(0, 177) + "..." : result;
    }

    private static final class Candidate {
        final long amount;
        final int score;
        Candidate(long amount, int score) { this.amount = amount; this.score = score; }
    }
}
