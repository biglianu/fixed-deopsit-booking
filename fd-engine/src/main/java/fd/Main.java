package fd;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** CLI: java fd.Main ratecard.csv holidays.csv commands.txt outdir */
public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: fd.Main <ratecard.csv> <holidays.csv> <commands.txt> <outdir>");
            System.exit(2);
        }
        RateCard rc = loadRateCard(Path.of(args[0]));
        Calendar cal = loadHolidays(Path.of(args[1]));
        Bank bank = new Bank(rc, cal);
        Path out = Path.of(args[3]);
        Files.createDirectories(out);
        List<String> lines = Files.readAllLines(Path.of(args[2]));
        List<String> report = new ArrayList<>();
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                Commands.execute(bank, line, report);
            } catch (RuntimeException e) {
                report.add("REJECTED line " + lineNo + ": " + e.getMessage());
            }
        }
        for (Deposit d : bank.all()) {
            report.add("FD " + d.id + " status=" + d.status + " principal=" + Money.fmt(d.principal));
            for (String entry : d.log) report.add("  " + d.id + ": " + entry);
        }
        Files.write(out.resolve("events.log"), report);
        System.out.println("Processed " + lines.size() + " lines -> " + out.resolve("events.log"));
    }

    static RateCard loadRateCard(Path p) throws IOException {
        RateCard rc = new RateCard();
        Map<LocalDate, List<RateCard.Band>> bandsByDate = new TreeMap<>();
        Map<LocalDate, BigDecimal[]> seniorByDate = new HashMap<>();
        for (String line : Files.readAllLines(p)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("effective_from")) continue;
            String[] f = line.split(",");
            // effective_from,min_days,max_days,amount_min,amount_max,rate,senior_addon,senior_min_days
            LocalDate eff = LocalDate.parse(f[0].strip());
            int minD = Integer.parseInt(f[1].strip());
            int maxD = Integer.parseInt(f[2].strip());
            BigDecimal amin = new BigDecimal(f[3].strip());
            BigDecimal amax = new BigDecimal(f[4].strip());
            BigDecimal rate = new BigDecimal(f[5].strip());
            BigDecimal senAdd = new BigDecimal(f[6].strip());
            int senMin = Integer.parseInt(f[7].strip());
            bandsByDate.computeIfAbsent(eff, k -> new ArrayList<>())
                    .add(new RateCard.Band(minD, maxD, amin, amax, rate));
            seniorByDate.put(eff, new BigDecimal[]{senAdd, BigDecimal.valueOf(senMin)});
        }
        for (var e : bandsByDate.entrySet()) {
            BigDecimal[] s = seniorByDate.get(e.getKey());
            rc.addVersion(new RateCard.Version(e.getKey(), e.getValue(), s[0], s[1].intValue()));
        }
        return rc;
    }

    static Calendar loadHolidays(Path p) throws IOException {
        Calendar c = new Calendar();
        for (String line : Files.readAllLines(p)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("date")) continue;
            c.addHoliday(LocalDate.parse(line.split(",")[0].strip()));
        }
        return c;
    }
}
