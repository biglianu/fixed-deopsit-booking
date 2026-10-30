package fd;

import java.time.*;
import java.util.*;

/** Holidays, calendar quarter ends and financial year (BR-02, BR-07). */
public final class Calendar {
    private final Set<LocalDate> holidays = new HashSet<>();

    public void addHoliday(LocalDate d) { holidays.add(d); }
    public boolean isHoliday(LocalDate d) { return holidays.contains(d); }

    /** Next business day on or after d. */
    public LocalDate nextBusinessDay(LocalDate d) {
        LocalDate x = d;
        while (isHoliday(x)) x = x.plusDays(1);
        return x;
    }

    /** First calendar quarter end strictly after d (Mar 31, Jun 30, Sep 30, Dec 31). */
    public static LocalDate firstQuarterEndAfter(LocalDate d) {
        int y = d.getYear();
        LocalDate[] candidates = {
                LocalDate.of(y, 3, 31), LocalDate.of(y, 6, 30),
                LocalDate.of(y, 9, 30), LocalDate.of(y, 12, 31),
                LocalDate.of(y + 1, 3, 31)};
        for (LocalDate q : candidates) if (q.isAfter(d)) return q;
        throw new IllegalStateException();
    }

    /** Quarter ends q with start < q <= end. */
    public static List<LocalDate> quarterEndsBetween(LocalDate start, LocalDate end) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate q = firstQuarterEndAfter(start);
        while (!q.isAfter(end)) {
            out.add(q);
            q = firstQuarterEndAfter(q);
        }
        return out;
    }

    /** Indian financial year (April to March) start year containing d. */
    public static int financialYear(LocalDate d) {
        return d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
    }
}
