package vn.phoneanh.vinhac;

import java.text.NumberFormat;
import java.util.Locale;

final class Format {
    static String money(long value) { return NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(value); }
}
