package fd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Command grammar (one per line):
 *   BOOK id customer TYPE MATURITY bookingDate tenure(7D|6M) amount senior(Y/N) pan(Y/N) form15(Y/N)
 *   PAYOUT id periodEnd
 *   PREMATURE_WITHDRAW id date
 *   PARTIAL_WITHDRAW id date amount
 *   LOAN_AGAINST_FD id date
 *   MATURE id
 */
public final class Commands {
    private Commands() {}

    public static void execute(Bank bank, String line, List<String> report) {
        String[] t = line.split("\\s+");
        switch (t[0]) {
            case "BOOK" -> {
                String tenure = t[6];
                Integer days = null, months = null;
                if (tenure.endsWith("D")) days = Integer.parseInt(tenure.substring(0, tenure.length() - 1));
                else if (tenure.endsWith("M")) months = Integer.parseInt(tenure.substring(0, tenure.length() - 1));
                else throw new IllegalArgumentException("Bad tenure " + tenure);
                Deposit d = bank.book(t[1], t[2], Deposit.Type.valueOf(t[3]), Deposit.Maturity.valueOf(t[4]),
                        LocalDate.parse(t[5]), days, months, new BigDecimal(t[7]),
                        t[8].equals("Y"), t[9].equals("Y"), t[10].equals("Y"));
                report.add("BOOKED " + d.id + " rate=" + d.rateAtBooking + " maturity=" + d.maturityDate);
            }
            case "PAYOUT" -> {
                BigDecimal net = bank.payout(t[1], LocalDate.parse(t[2]));
                report.add("PAYOUT " + t[1] + " " + t[2] + " net=" + net);
            }
            case "PREMATURE_WITHDRAW" -> {
                BigDecimal net = bank.prematureWithdraw(t[1], LocalDate.parse(t[2]));
                report.add("PREMATURE_WITHDRAW " + t[1] + " net=" + net);
            }
            case "PARTIAL_WITHDRAW" -> {
                BigDecimal net = bank.partialWithdraw(t[1], LocalDate.parse(t[2]), new BigDecimal(t[3]));
                report.add("PARTIAL_WITHDRAW " + t[1] + " net=" + net);
            }
            case "LOAN_AGAINST_FD" -> {
                BigDecimal limit = bank.loanAgainst(t[1], LocalDate.parse(t[2]));
                report.add("LOAN " + t[1] + " limit=" + limit);
            }
            case "MATURE" -> {
                bank.mature(t[1]);
                report.add("MATURED " + t[1]);
            }
            default -> throw new IllegalArgumentException("Unknown command " + t[0]);
        }
    }
}
