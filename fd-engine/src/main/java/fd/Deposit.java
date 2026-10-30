package fd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** One FD booking and its lifecycle state. */
public final class Deposit {
    public enum Type { CUMULATIVE, QUARTERLY_PAYOUT, MONTHLY_PAYOUT }
    public enum Maturity { RENEW_PRINCIPAL, RENEW_ALL, PAYOUT }
    public enum Status { ACTIVE, CLOSED, LIEN }

    public final String id;
    public final String customerId;
    public final Type type;
    public final Maturity maturityInstruction;
    public final LocalDate bookingDate;
    public final int tenureDays;
    public final LocalDate maturityDate;
    public final boolean senior;
    public final boolean panMissing;
    public final boolean form15;
    public final BigDecimal rateAtBooking;
    public BigDecimal principal;
    public Status status = Status.ACTIVE;
    public BigDecimal loanOutstanding = Money.ZERO;
    public final List<String> log = new ArrayList<>();

    public Deposit(String id, String customerId, Type type, Maturity maturityInstruction,
                   LocalDate bookingDate, int tenureDays, LocalDate maturityDate,
                   boolean senior, boolean panMissing, boolean form15,
                   BigDecimal rateAtBooking, BigDecimal principal) {
        this.id = id;
        this.customerId = customerId;
        this.type = type;
        this.maturityInstruction = maturityInstruction;
        this.bookingDate = bookingDate;
        this.tenureDays = tenureDays;
        this.maturityDate = maturityDate;
        this.senior = senior;
        this.panMissing = panMissing;
        this.form15 = form15;
        this.rateAtBooking = rateAtBooking;
        this.principal = principal;
    }
}
