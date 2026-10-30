package fd;

import java.math.BigDecimal;
import java.util.*;

/** BR-04: per-customer per-FY interest tracking and TDS deduction. */
public final class Tds {
    public record Key(String customerId, int fy) {}

    private final Map<Key, BigDecimal> interestYtd = new HashMap<>();

    /** Record interest credited/paid and return TDS to deduct (rupee-rounded). */
    public BigDecimal credit(String customerId, int fy, BigDecimal interest, boolean senior,
                             boolean panMissing, boolean form15) {
        Key k = new Key(customerId, fy);
        BigDecimal before = interestYtd.getOrDefault(k, BigDecimal.ZERO);
        BigDecimal after = before.add(interest);
        interestYtd.put(k, after);

        if (form15) return Money.ZERO.setScale(2);
        BigDecimal threshold = new BigDecimal(senior ? "50000" : "40000");
        if (after.compareTo(threshold) <= 0) return Money.ZERO.setScale(2);

        // Only interest above the threshold is taxable in the crossing credit.
        BigDecimal taxable = after.subtract(threshold.max(before));
        BigDecimal rate = panMissing ? new BigDecimal("0.20") : new BigDecimal("0.10");
        return Money.rupee(taxable.multiply(rate));
    }

    public BigDecimal ytd(String customerId, int fy) {
        return interestYtd.getOrDefault(new Key(customerId, fy), BigDecimal.ZERO);
    }
}
