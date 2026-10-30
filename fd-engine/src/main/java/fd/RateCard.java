package fd;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** BR-01 rate-card lookup and BR-11 versioning by effective date. */
public final class RateCard {

    /** tenure band in days (inclusive) and amount band [amountMin, amountMax). */
    public record Band(int minDays, int maxDays, BigDecimal amountMin, BigDecimal amountMax, BigDecimal rate) {}

    public record Version(LocalDate effectiveFrom, List<Band> bands, BigDecimal seniorAddOn, int seniorMinDays) {}

    private final List<Version> versions = new ArrayList<>();

    public void addVersion(Version v) {
        versions.add(v);
        versions.sort(Comparator.comparing(Version::effectiveFrom));
    }

    /** Card effective on a date: latest version with effectiveFrom <= date. */
    public Version cardOn(LocalDate date) {
        Version found = null;
        for (Version v : versions) {
            if (!v.effectiveFrom().isAfter(date)) found = v;
        }
        if (found == null) throw new IllegalStateException("No rate card effective on " + date);
        return found;
    }

    /** Base rate (without senior add-on) for tenure and principal using the card on the given date. */
    public BigDecimal baseRate(LocalDate date, int tenureDays, BigDecimal principal) {
        for (Band b : cardOn(date).bands()) {
            boolean tenureOk = tenureDays >= b.minDays() && tenureDays <= b.maxDays();
            boolean amountOk = principal.compareTo(b.amountMin()) >= 0 && principal.compareTo(b.amountMax()) < 0;
            if (tenureOk && amountOk) return b.rate();
        }
        throw new IllegalArgumentException("No rate band for tenure " + tenureDays + " amount " + principal);
    }

    /** Full rate for a customer: base plus SENIOR add-on for tenures >= seniorMinDays (BR-01). */
    public BigDecimal rate(LocalDate date, int tenureDays, BigDecimal principal, boolean senior) {
        Version v = cardOn(date);
        BigDecimal r = baseRate(date, tenureDays, principal);
        if (senior && tenureDays >= v.seniorMinDays()) r = r.add(v.seniorAddOn());
        return r;
    }
}
