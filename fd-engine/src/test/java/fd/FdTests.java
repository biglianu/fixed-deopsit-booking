package fd;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Self-checks for business rules. Run: java -cp out fd.FdTests */
public final class FdTests {
    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    static RateCard card() {
        RateCard rc = new RateCard();
        rc.addVersion(new RateCard.Version(LocalDate.of(2026, 1, 1), java.util.List.of(
                new RateCard.Band(7, 45, bd("5000"), bd("20000000"), bd("4.00")),
                new RateCard.Band(46, 179, bd("5000"), bd("20000000"), bd("5.00")),
                new RateCard.Band(180, 364, bd("5000"), bd("20000000"), bd("6.50")),
                new RateCard.Band(365, 3650, bd("5000"), bd("20000000"), bd("7.00"))),
                bd("0.50"), 180));
        return rc;
    }

    static BigDecimal bd(String s) { return new BigDecimal(s); }

    public static void main(String[] args) {
        RateCard rc = card();
        // BR-01 senior add-on boundary
        check("senior add-on applies at 180 days",
                rc.rate(LocalDate.of(2026, 2, 1), 180, bd("100000"), true).compareTo(bd("7.00")) == 0);
        check("senior add-on not applied at 179 days",
                rc.rate(LocalDate.of(2026, 2, 1), 179, bd("100000"), true).compareTo(bd("5.00")) == 0);

        // BR-11 card effective on the date
        RateCard two = card();
        two.addVersion(new RateCard.Version(LocalDate.of(2026, 7, 1), java.util.List.of(
                new RateCard.Band(180, 364, bd("5000"), bd("20000000"), bd("7.00"))), bd("0.50"), 180));
        check("card effective on maturity date is used for renewal rate",
                two.baseRate(LocalDate.of(2026, 7, 1), 200, bd("100000")).compareTo(bd("7.00")) == 0);
        check("earlier card is used before effective date",
                two.baseRate(LocalDate.of(2026, 6, 30), 200, bd("100000")).compareTo(bd("6.50")) == 0);

        // BR-03 minimum principal and tenure
        Bank bank = new Bank(rc, new Calendar());
        try {
            bank.book("X1", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                    LocalDate.of(2026, 1, 1), 30, null, bd("4999"), false, false, false);
            check("reject amount below Rs 5000", false);
        } catch (IllegalArgumentException e) { check("reject amount below Rs 5000", true); }
        try {
            bank.book("X2", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                    LocalDate.of(2026, 1, 1), 6, null, bd("10000"), false, false, false);
            check("reject tenure under 7 days", false);
        } catch (IllegalArgumentException e) { check("reject tenure under 7 days", true); }

        // BR-03 month tenure, 31 Aug + 6 months = 28 Feb (non-leap year)
        Deposit d = bank.book("X3", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                LocalDate.of(2026, 8, 31), null, 6, bd("10000"), false, false, false);
        check("31 Aug + 6 months lands on 28 Feb 2027", d.maturityDate.equals(LocalDate.of(2027, 2, 28)));

        // BR-02 quarter ends
        check("quarter ends between Jan 10 and Dec 31 are 4 dates",
                Calendar.quarterEndsBetween(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 12, 31)).size() == 4);

        // BR-06 partial withdrawal multiple of 1000
        try {
            bank.partialWithdraw("X3", LocalDate.of(2026, 9, 30), bd("1500"));
            check("reject partial withdrawal not in multiples of 1000", false);
        } catch (IllegalArgumentException e) { check("reject partial withdrawal not in multiples of 1000", true); }

        // BR-06 leaving exactly 5000 is allowed
        Bank b2 = new Bank(rc, new Calendar());
        b2.book("Y1", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                LocalDate.of(2026, 1, 1), 365, null, bd("10000"), false, false, false);
        try {
            b2.partialWithdraw("Y1", LocalDate.of(2026, 6, 1), bd("5000"));
            check("partial withdrawal leaving exactly Rs 5000 is allowed", true);
        } catch (IllegalArgumentException e) { check("partial withdrawal leaving exactly Rs 5000 is allowed", false); }

        // BR-05 withdrawal within 7 days: principal only, no interest
        Bank b3 = new Bank(rc, new Calendar());
        b3.book("Z1", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                LocalDate.of(2026, 1, 1), 365, null, bd("10000"), false, false, false);
        check("withdrawal on day 7 returns principal only",
                b3.prematureWithdraw("Z1", LocalDate.of(2026, 1, 8)).compareTo(bd("10000.00")) == 0);

        // Lifecycle: closed FD rejects operations
        try {
            b3.prematureWithdraw("Z1", LocalDate.of(2026, 1, 20));
            check("reject operation on closed FD", false);
        } catch (IllegalStateException e) { check("reject operation on closed FD", true); }

        // Lien: FD with loan cannot be withdrawn prematurely
        Bank b4 = new Bank(rc, new Calendar());
        b4.book("L1", "C", Deposit.Type.CUMULATIVE, Deposit.Maturity.PAYOUT,
                LocalDate.of(2026, 1, 1), 365, null, bd("100000"), false, false, false);
        b4.loanAgainst("L1", LocalDate.of(2026, 3, 1));
        try {
            b4.prematureWithdraw("L1", LocalDate.of(2026, 4, 1));
            check("reject premature withdrawal on lien-marked FD", false);
        } catch (IllegalStateException e) { check("reject premature withdrawal on lien-marked FD", true); }

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
