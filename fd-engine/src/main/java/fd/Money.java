package fd;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money as BigDecimal. Accrual at paisa (scale 2); payouts and TDS at whole rupee (BR-10). */
public final class Money {
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    private Money() {}

    public static BigDecimal of(String s) { return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal paisa(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    public static BigDecimal rupee(BigDecimal v) { return v.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP); }
    public static String fmt(BigDecimal v) { return paisa(v).toPlainString(); }
}
