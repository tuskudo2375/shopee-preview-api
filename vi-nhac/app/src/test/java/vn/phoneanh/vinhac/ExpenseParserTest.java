package vn.phoneanh.vinhac;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ExpenseParserTest {
    @Test public void understandsVietnameseInputs() {
        assertParsed("ăn trưa 30k", 30_000, "Nhu cầu");
        assertParsed("trà sữa 50k", 50_000, "Ăn vặt");
        assertParsed("đổ xăng 100.000", 100_000, "Đi lại");
        assertParsed("tiền nhà 1.500.000", 1_500_000, "Hóa đơn");
        assertParsed("mua đồ 1,5 triệu", 1_500_000, "Khác");
    }
    private void assertParsed(String raw, long amount, String category) {
        ExpenseParser.Result result = ExpenseParser.parse(raw);
        assertEquals(amount, result.amount); assertEquals(category, result.category);
    }
}
