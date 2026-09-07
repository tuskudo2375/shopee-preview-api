package titus.expenseassistant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Currency must belong to the debit amount, never an account number or balance. */
final class NotificationExpenseParser {
    private static final Pattern MONEY = Pattern.compile(
            "(?<![a-z0-9])([+-]?)\\s*(?:(vnd|₫|d)\\s*)?(\\d+(?:[.,]\\d+)*)(?:\\s*(vnd|₫|d)(?![a-z]))?");
    private static final Pattern NON_TRANSACTION = Pattern.compile(
            "(?:so du|balance|con lai|han muc|available|tai khoan|account|ma gd|otp)[^0-9\\n;•]{0,24}$");
    private static final Pattern DEBIT_CONTEXT = Pattern.compile(
            "(?:thanh toan|chi tieu|mua hang|da su dung|trich no|bi tru|da tru|debit(?:ed)?|payment|spent|paid)(?:[^0-9\\n;•]{0,45})$");

    private NotificationExpenseParser() {}

    static ExpenseParser.Result parse(String title, String body, String packageName,
                                      List<String> categories, List<String> sources) {
        String safeTitle = title == null ? "" : title.trim();
        String safeBody = body == null ? "" : body.trim();
        String combined = safeTitle + "\n" + safeBody;
        String plain = normalize(combined);
        if (has(plain, "nhan tien", "ghi co", "cong tien", "hoan tien", "refund", "cashback",
                "nap tien", "incoming", "received", "chuyen den", "that bai", "khong thanh cong",
                "failed", "declined", "tu choi", "uu dai", "khuyen mai")) return null;

        Matcher matcher = MONEY.matcher(plain);
        long amount = 0;
        int bestScore = 0;
        while (matcher.find()) {
            if (matcher.group(2) == null && matcher.group(4) == null) continue;
            if ("+".equals(matcher.group(1))) continue;
            String before = plain.substring(Math.max(0, matcher.start() - 90), matcher.start());
            if (NON_TRANSACTION.matcher(before).find()) continue;
            boolean negative = "-".equals(matcher.group(1));
            if (!negative && !DEBIT_CONTEXT.matcher(before).find()) continue;
            long candidate = parseNumber(matcher.group(3));
            if (candidate <= 0) continue;
            int score = negative ? 2 : 1;
            if (score > bestScore) { amount = candidate; bestScore = score; }
            else if (score == bestScore && candidate != amount) return null; // Multiple debits: ambiguous.
        }
        if (amount == 0) return null;
        String source = findSource(plain, packageName, sources);
        if (source.isEmpty()) return null;
        ExpenseParser.Result classified = ExpenseParser.parse(safeBody, categories, sources);
        String note = combined.replaceAll("\\s+", " ").trim();
        if (note.length() > 180) note = note.substring(0, 177) + "...";
        return new ExpenseParser.Result(amount, classified.category, note, source);
    }

    private static long parseNumber(String raw) {
        try {
            String number = raw;
            if (raw.matches("\\d{1,3}(?:[.,]\\d{3})+")) number = raw.replaceAll("[.,]", "");
            else if (raw.matches("\\d{1,3}(?:,\\d{3})+\\.\\d{2}")) number = raw.replace(",", "");
            else if (raw.matches("\\d{1,3}(?:\\.\\d{3})+,\\d{2}")) number = raw.replace(".", "").replace(',', '.');
            else if (raw.matches("\\d+[.,]\\d{1,2}")) number = raw.replace(',', '.');
            else if (!raw.matches("\\d+")) return 0;
            return new BigDecimal(number).longValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) { return 0; }
    }

    private static String findSource(String text, String packageName, List<String> sources) {
        String bank = normalize(packageName == null ? "" : packageName) + " " + text;
        // A bank's brand is not evidence of a credit-card transaction.
        boolean card = Pattern.compile("\\b(the|card|credit)\\b").matcher(text).find();
        if (card) {
            if (has(bank, "techcombank", "tcb", "the tech")) return ExpenseParser.explicitSource("tech", sources);
            if (has(bank, "tpbank", "tp bank", "the tp")) return ExpenseParser.explicitSource("tp", sources);
            if (has(bank, "vib")) return ExpenseParser.explicitSource("vib", sources);
            return "";
        }
        if (has(text, "tai khoan", "account", "so du", "balance", "bien dong", "chuyen khoan", "transfer")
                || has(bank, "techcombank", "tpbank", "com.mbmobile", "vib")) {
            return ExpenseParser.explicitSource("bank", sources);
        }
        return "";
    }

    private static String normalize(String text) {
        return ExpenseParser.plain(text.toLowerCase(Locale.ROOT)).replace('\u2212', '-').replace('\u2013', '-')
                .replace('\u00a0', ' ').replace('\u202f', ' ');
    }

    private static boolean has(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }
}
