package titus.expenseassistant;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationExpenseParserTest {
    private ExpenseParser.Result parse(String title, String body, String pkg) {
        return NotificationExpenseParser.parse(title, body, pkg,
                Arrays.asList(BudgetStore.DEFAULT_CATEGORIES), Arrays.asList(BudgetStore.DEFAULT_SOURCES));
    }

    @Test public void screenshotCurrencyAfterNegativeAmount() {
        ExpenseParser.Result result = parse("Biến động số dư", "Số tiền giao dịch: -30.000VND\nSố dư: 0VND", "bank.app");
        assertNotNull(result); assertEquals(30000, result.amount); assertEquals("Chuyển khoản", result.source);
    }

    @Test public void screenshotCurrencyBeforeAmountDoesNotReadAccountOrBalance() {
        ExpenseParser.Result result = parse("- VND 30,000", "Tài khoản: 1234567890\nSố dư: VND 2,085,770", "vn.com.techcombank.bb.app");
        assertNotNull(result); assertEquals(30000, result.amount); assertEquals("Chuyển khoản", result.source);
    }

    @Test public void unicodeMinusAndSpaces() {
        assertEquals(30000, parse("− VND\u00a030,000", "Tài khoản: 1234567890", "bank.app").amount);
        assertEquals(30000, parse("Biến động số dư", "Số tiền giao dịch: –30.000đ\nSố dư 0đ", "bank.app").amount);
    }

    @Test public void explicitCardIsDifferentFromAccount() {
        ExpenseParser.Result result = parse("Techcombank", "Thẻ tín dụng thanh toán VND 30,000 tại cửa hàng", "vn.com.techcombank.bb.app");
        assertNotNull(result); assertEquals(30000,result.amount); assertEquals("Thẻ Tech", result.source);
        assertEquals("Thẻ TP", parse("TPBank", "Thẻ TP đã sử dụng 50.000VND", "tpbank").source);
    }

    @Test public void ignoresCreditsBalancesPromotionsAndFailures() {
        assertNull(parse("+ VND 30,000", "Tài khoản: 1234567890\nSố dư: VND 2,085,770", "vn.com.techcombank.bb.app"));
        assertNull(parse("Biến động số dư", "Số tiền giao dịch: +30.000VND\nSố dư: 90.000VND", "bank.app"));
        assertNull(parse("Biến động số dư", "Số dư: -30.000VND", "bank.app"));
        assertNull(parse("Ưu đãi tới 40% - Phí từ 30.000VND", "Khi mua bảo hiểm", "vn.com.techcombank.bb.app"));
        assertNull(parse("Thanh toán thất bại", "Thẻ Tech thanh toán 30.000VND", "vn.com.techcombank.bb.app"));
        assertNull(parse("Hoàn tiền", "Thẻ Tech -30.000VND", "vn.com.techcombank.bb.app"));
        assertNull(parse("Transaction", "Tài khoản 1234567890 - Số dư VND 2,085,770", "vn.com.techcombank.bb.app"));
    }

    @Test public void supportsWholeDongDecimalsAndRejectsAmbiguity() {
        assertEquals(30000, parse("- VND 30,000.00", "Tài khoản 1234", "bank.app").amount);
        assertEquals(30000, parse("-30.000,00 VND", "Tài khoản 1234", "bank.app").amount);
        assertNull(parse("Biến động số dư", "-30.000VND\n-50.000VND", "bank.app"));
        assertNull(parse("-VND 9999999999999999999999999", "Tài khoản 1234", "bank.app"));
    }
}
