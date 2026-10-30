package fd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Orchestrates bookings, payouts, premature withdrawal, loans, and maturity (BR-03, BR-05..BR-09).
 */
public final class Bank {
    private final RateCard rateCard;
    private final Calendar calendar;
    private final Tds tds = new Tds();
    private final Map<String, Deposit> deposits = new LinkedHashMap<>();
    private final Map<String, List<String>> outputByFd = new LinkedHashMap<>();
    private final Map<String, BigDecimal> payoutTotals = new HashMap<>();
    private static final BigDecimal SAVINGS_RATE = new BigDecimal("3");
    private static final BigDecimal PENALTY = new BigDecimal("1");
    private static final BigDecimal MIN_PRINCIPAL = new BigDecimal("5000");
    private static final BigDecimal LOAN_LTV = new BigDecimal("0.90");
    private static final BigDecimal LOAN_SPREAD = new BigDecimal("2");

    public Bank(RateCard rateCard, Calendar calendar) {
        this.rateCard = rateCard;
        this.calendar = calendar;
    }

    /** BR-03: book an FD. tenureMonths > 0 takes precedence over tenureDays. */
    public Deposit book(String id, String customerId, Deposit.Type type, Deposit.Maturity mi,
                        LocalDate bookingDate, Integer tenureDays, Integer tenureMonths,
                        BigDecimal principal, boolean senior, boolean panMissing, boolean form15) {
        if (principal.compareTo(MIN_PRINCIPAL) < 0)
            throw new IllegalArgumentException("Minimum principal is Rs 5000");
        LocalDate maturity;
        int days;
        if (tenureMonths != null) {
            maturity = addMonthsSameDay(bookingDate, tenureMonths);
            days = (int) ChronoUnit.DAYS.between(bookingDate, maturity);
        } else {
            days = tenureDays;
            maturity = bookingDate.plusDays(days);
        }
        if (days < 7) throw new IllegalArgumentException("Minimum tenure is 7 days");
        if (days > 3650) throw new IllegalArgumentException("Maximum tenure is 10 years");
        BigDecimal rate = rateCard.rate(bookingDate, days, principal, senior);
        Deposit d = new Deposit(id, customerId, type, mi, bookingDate, days, maturity,
                senior, panMissing, form15, rate, principal);
        deposits.put(id, d);
        d.log.add("BOOKED rate=" + rate + " maturity=" + maturity + " days=" + days);
        return d;
    }

    /** BR-03: same day-of-month; if missing in target month, last day of that month. */
    public static LocalDate addMonthsSameDay(LocalDate d, int months) {
        LocalDate target = d.plusMonths(months);
        return target;
    }

    public Deposit get(String id) {
        Deposit d = deposits.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown FD " + id);
        return d;
    }

    public Collection<Deposit> all() { return deposits.values(); }

    public Calendar calendar() { return calendar; }
    public Tds tds() { return tds; }

    /** Amount of interest the FD would earn at maturity for a given run tenure under the booking card (BR-05). */
    public BigDecimal interestForRunTenure(Deposit d, LocalDate endDate) {
        long runDays = ChronoUnit.DAYS.between(d.bookingDate, endDate);
        int runDaysInt = (int) runDays;
        BigDecimal rate = rateCard.rate(d.bookingDate, runDaysInt, d.principal, d.senior);
        return Interest.cumulativeMaturity(d.principal, rate, d.bookingDate, endDate).subtract(d.principal);
    }

    /** BR-05 full premature withdrawal. Returns the net amount paid out to the customer. */
    public BigDecimal prematureWithdraw(String fdId, LocalDate date) {
        Deposit d = get(fdId);
        if (d.status == Deposit.Status.CLOSED) throw new IllegalStateException("FD " + fdId + " is closed");
        if (d.status == Deposit.Status.LIEN) throw new IllegalStateException("FD " + fdId + " is lien-marked");
        long held = ChronoUnit.DAYS.between(d.bookingDate, date);
        if (held <= 7) {
            d.status = Deposit.Status.CLOSED;
            d.log.add("PREMATURE_WITHDRAW on " + date + " (<=7 days) principal returned, no interest");
            return d.principal;
        }
        BigDecimal interest = interestForRunTenure(d, date);
        BigDecimal penaltyRate = PENALTY;
        BigDecimal rateForRun = rateCard.rate(d.bookingDate, (int) held, d.principal, d.senior);
        BigDecimal reducedRate = rateForRun.subtract(penaltyRate).max(BigDecimal.ZERO);
        BigDecimal recalculated = Interest.cumulativeMaturity(d.principal, reducedRate, d.bookingDate, date)
                .subtract(d.principal);
        BigDecimal paidSoFar = payoutTotals.getOrDefault(d.id, BigDecimal.ZERO);
        BigDecimal net = d.principal.add(recalculated).subtract(paidSoFar);
        d.status = Deposit.Status.CLOSED;
        d.log.add("PREMATURE_WITHDRAW on " + date + " held=" + held + " reRateInterest=" + Money.fmt(recalculated)
                + " recovered=" + Money.fmt(paidSoFar) + " net=" + Money.fmt(net));
        return Money.rupee(net);
    }

    /** BR-06 partial withdrawal. amount in multiples of 1000, remaining principal must be >= 5000. */
    public BigDecimal partialWithdraw(String fdId, LocalDate date, BigDecimal amount) {
        Deposit d = get(fdId);
        if (amount.remainder(new BigDecimal("1000")).signum() != 0)
            throw new IllegalArgumentException("Partial withdrawal must be a multiple of 1000");
        BigDecimal remaining = d.principal.subtract(amount);
        if (remaining.compareTo(MIN_PRINCIPAL) < 0)
            throw new IllegalArgumentException("Remaining principal must be at least 5000");
        // Withdrawn slice is closed prematurely at the rate for its run tenure; remaining keeps original rate and maturity.
        BigDecimal sliceInterest = interestForRunTenure(d, date).multiply(amount)
                .divide(d.principal, 40, java.math.RoundingMode.HALF_UP);
        d.principal = remaining;
        d.log.add("PARTIAL_WITHDRAW " + amount + " on " + date + " sliceInterest=" + Money.fmt(sliceInterest)
                + " remaining=" + remaining);
        return Money.rupee(amount.add(sliceInterest));
    }

    /** Pay periodic interest for a payout FD up to date (BR-02 payouts, BR-04 TDS). */
    public BigDecimal payout(String fdId, LocalDate periodEnd) {
        Deposit d = get(fdId);
        if (d.type == Deposit.Type.CUMULATIVE) return Money.ZERO;
        long days = ChronoUnit.DAYS.between(d.bookingDate, periodEnd);
        BigDecimal rate = d.rateAtBooking;
        BigDecimal interest;
        if (d.type == Deposit.Type.QUARTERLY_PAYOUT) {
            interest = Interest.simple(d.principal, rate, days);
        } else {
            BigDecimal qEq = Interest.quarterlyEquivalent(rate).multiply(Interest.MONTHLY_DISCOUNT);
            interest = Interest.simple(d.principal, qEq.multiply(new BigDecimal("4")), days);
        }
        BigDecimal payable = Money.rupee(interest);
        BigDecimal t = tds.credit(d.customerId, Calendar.financialYear(periodEnd), payable,
                d.senior, d.panMissing, d.form15);
        payoutTotals.merge(d.id, payable, BigDecimal::add);
        d.log.add("PAYOUT " + periodEnd + " gross=" + payable + " tds=" + t);
        return payable.subtract(t);
    }

    /** BR-09 overdue: PAYOUT instruction with no claim after 14 days renews for same tenure at the then rate. */
    public void applyOverdueRenewal(String fdId, LocalDate today) {
        Deposit d = get(fdId);
        if (d.maturityInstruction != Deposit.Maturity.PAYOUT) return;
        if (ChronoUnit.DAYS.between(d.maturityDate, today) < 14) return;
        renew(d, today);
        d.log.add("OVERDUE auto-renewal on " + today);
    }

    /** BR-07 auto-renewal and maturity settlement on the maturity date (holiday-aware). */
    public void mature(String fdId) {
        Deposit d = get(fdId);
        LocalDate actual = calendar.nextBusinessDay(d.maturityDate);
        long extra = ChronoUnit.DAYS.between(d.maturityDate, actual);
        BigDecimal maturityValue = Interest.cumulativeMaturity(d.principal, d.rateAtBooking,
                d.bookingDate, d.maturityDate);
        if (extra > 0) {
            BigDecimal extraInt = d.principal.multiply(SAVINGS_RATE, new java.math.MathContext(40))
                    .multiply(BigDecimal.valueOf(extra))
                    .divide(new BigDecimal("36500"), new java.math.MathContext(40));
            maturityValue = maturityValue.add(extraInt);
        }
        switch (d.maturityInstruction) {
            case RENEW_ALL -> {
                d.log.add("MATURE RENEW_ALL on " + actual + " value=" + Money.fmt(maturityValue));
                renew(d, actual, maturityValue);
            }
            case RENEW_PRINCIPAL -> {
                BigDecimal interest = maturityValue.subtract(d.principal);
                BigDecimal t = tds.credit(d.customerId, Calendar.financialYear(actual),
                        Money.rupee(interest), d.senior, d.panMissing, d.form15);
                d.log.add("MATURE RENEW_PRINCIPAL on " + actual + " interestPaid=" + Money.rupee(interest) + " tds=" + t);
                renew(d, actual, d.principal);
            }
            case PAYOUT -> {
                BigDecimal interest = maturityValue.subtract(d.principal).subtract(
                        payoutTotals.getOrDefault(d.id, BigDecimal.ZERO));
                BigDecimal t = tds.credit(d.customerId, Calendar.financialYear(actual),
                        Money.rupee(interest), d.senior, d.panMissing, d.form15);
                d.status = Deposit.Status.CLOSED;
                d.log.add("MATURE PAYOUT on " + actual + " amount=" + Money.rupee(interest).subtract(t) + " tds=" + t);
            }
        }
    }

    /** Renew: book a new term at the card effective on the renewal date for the same tenure. */
    private void renew(Deposit old, LocalDate on) {
        renew(old, on, old.principal);
    }

    private void renew(Deposit old, LocalDate on, BigDecimal newPrincipal) {
        BigDecimal rate = rateCard.rate(on, old.tenureDays, newPrincipal, old.senior);
        LocalDate newMat = on.plusDays(old.tenureDays);
        Deposit n = new Deposit(old.id + "-R", old.customerId, old.type, old.maturityInstruction,
                on, old.tenureDays, newMat, old.senior, old.panMissing, old.form15, rate, newPrincipal);
        deposits.put(n.id, n);
        old.status = Deposit.Status.CLOSED;
        n.log.add("RENEWED from " + old.id + " rate=" + rate + " maturity=" + newMat);
    }

    /** BR-08 loan against FD: up to 90% of principal plus accrued interest. */
    public BigDecimal loanAgainst(String fdId, LocalDate date) {
        Deposit d = get(fdId);
        if (d.status != Deposit.Status.ACTIVE) throw new IllegalStateException("FD not active");
        // BR-08: accrued interest is computed at the FD's own booking rate.
        BigDecimal accrued = Interest.cumulativeMaturity(d.principal, d.rateAtBooking, d.bookingDate, date)
                .subtract(d.principal);
        BigDecimal base = d.principal.add(accrued);
        BigDecimal limit = base.multiply(LOAN_LTV).setScale(2, java.math.RoundingMode.HALF_DOWN);
        d.loanOutstanding = limit;
        d.status = Deposit.Status.LIEN;
        d.log.add("LOAN_AGAINST_FD on " + date + " limit=" + Money.fmt(limit)
                + " rate=" + d.rateAtBooking.add(LOAN_SPREAD));
        return Money.rupee(limit);
    }

    /** Test/diagnostic hook: record a payout total so premature recovery can reference it. */
    public void recordPayoutTotal(String fdId, BigDecimal amount) {
        payoutTotals.merge(fdId, amount, BigDecimal::add);
    }
}
