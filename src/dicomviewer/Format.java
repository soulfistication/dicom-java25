//
// Format.java
// Small numeric/text formatting helpers.
//

package dicomviewer;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class Format {
    private Format() {}

    // Formats a double to the given number of significant digits, stripping
    // trailing zeros (mirrors JavaScript's +v.toPrecision(n)).
    public static String significant(double v, int digits) {
        if (Double.isNaN(v)) return "NaN";
        if (Double.isInfinite(v)) return v > 0 ? "Infinity" : "-Infinity";
        if (v == 0.0) return "0";
        BigDecimal bd = new BigDecimal(v, new MathContext(digits, RoundingMode.HALF_UP));
        bd = bd.stripTrailingZeros();
        String s = bd.toPlainString();
        // Avoid "-0" style artefacts.
        if ("-0".equals(s)) return "0";
        return s;
    }

    public static String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format("%d KB", Math.round(n / 1024.0));
        return String.format("%.1f MB", n / 1024.0 / 1024.0);
    }
}
