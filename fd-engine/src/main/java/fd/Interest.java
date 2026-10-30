package fd;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Interest computation: BR-02 cumulative compounding and payout simple interest. */
public final class Interest {
    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_UP);
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    /** Monthly payout discount factor applied to quarterly-equivalent rate. */
    public static final BigDecimal MONTHLY_DISCOUNT = new BigDecimal("0.9975");

    private Interest() {}

    /**
     * Cumulative maturity amount: quarterly compounding on calendar quarter ends.
     * The first broken period (start to first quarter end) and the last broken period
     * (last quarter end to maturity) are compounded on actual days; full quarters use
     * quarterly rate rate/4 per quarter.
     * Returns the total maturity value (principal + interest), unrounded to paisa.
     */
    public static BigDecimal cumulativeMaturity(BigDecimal principal, BigDecimal annualRatePct,
                                                LocalDate start, LocalDate end) {
        BigDecimal r = annualRatePct.divide(HUNDRED, MC);
        BigDecimal quarterlyRate = r.divide(new BigDecimal("4"), MC);
        BigDecimal value = principal;
        LocalDate cursor = start;
        for (LocalDate q : Calendar.quarterEndsBetween(start, end)) {
            // A period starting on a quarter end is a full quarter; otherwise it is a broken period on actual days.
            if (isQuarterEnd(cursor)) {
                value = value.multiply(BigDecimal.ONE.add(quarterlyRate), MC);
            } else {
                long days = ChronoUnit.DAYS.between(cursor, q);
                value = value.multiply(BigDecimal.ONE.add(r.multiply(BigDecimal.valueOf(days), MC)
                        .divide(DAYS_IN_YEAR, MC)), MC);
            }
            cursor = q;
        }
        long lastDays = ChronoUnit.DAYS.between(cursor, end);
        if (lastDays > 0) {
            if (isQuarterEnd(cursor)) {
                // Cannot be a full quarter, so use actual days.
            }
            value = value.multiply(BigDecimal.ONE.add(r.multiply(BigDecimal.valueOf(lastDays), MC)
                    .divide(DAYS_IN_YEAR, MC)), MC);
        }
        return value;
    }

    private static boolean isQuarterEnd(LocalDate d) {
        return (d.getMonthValue() == 3 && d.getDayOfMonth() == 31)
                || (d.getMonthValue() == 6 && d.getDayOfMonth() == 30)
                || (d.getMonthValue() == 9 && d.getDayOfMonth() == 30)
                || (d.getMonthValue() == 12 && d.getDayOfMonth() == 31);
    }

    /** Simple interest for payout types over an actual-day period at annual rate pct. */
    public static BigDecimal simple(BigDecimal principal, BigDecimal annualRatePct, long days) {
        return principal.multiply(annualRatePct, MC).multiply(BigDecimal.valueOf(days), MC)
                .divide(HUNDRED.multiply(DAYS_IN_YEAR), MC);
    }

    /** Quarterly-equivalent interest rate for a given annual rate (BR-02). */
    public static BigDecimal quarterlyEquivalent(BigDecimal annualRatePct) {
        return annualRatePct.divide(new BigDecimal("4"), MC);
    }
}
