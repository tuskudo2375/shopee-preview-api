package titus.expenseassistant;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ExpenseParserTest {
    @Test public void understandsVietnameseInputs() {
        assertParsed("ăn trưa 30k", 30_000, "Ăn uống");
        assertParsed("trà sữa 50k", 50_000, "Ăn uống");
        assertParsed("đổ xăng 100.000", 100_000, "Di chuyển");
        assertParsed("tiền nhà 1.500.000", 1_500_000, "Hóa đơn");
        assertParsed("mua đồ 1,5 triệu", 1_500_000, "Mua sắm");
        ExpenseParser.Result lunchByBank = ExpenseParser.parse("ăn trưa 30k chuyển khoản");
        assertEquals("Ăn uống", lunchByBank.category);
        assertEquals("Chuyển khoản", lunchByBank.source);
        assertEquals("Ăn uống", ExpenseParser.parse("ăn trưa 25k bank").category);
        assertEquals("Chuyển khoản", ExpenseParser.parse("ăn trưa 25k bank").source);
        assertEquals("Thẻ TP", ExpenseParser.parse("cà hê 25k TP").source);
        assertEquals("Ăn uống", ExpenseParser.parse("cà hê 25k TP").category);
    }
    private void assertParsed(String raw, long amount, String category) {
        ExpenseParser.Result result = ExpenseParser.parse(raw);
        assertEquals(amount, result.amount); assertEquals(category, result.category);
    }
}
