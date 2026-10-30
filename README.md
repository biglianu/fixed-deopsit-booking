# PS-018: Fixed Deposit Booking, Renewal and Premature Withdrawal Engine

A command-file driven term-deposit engine written in Java 17+. It books cumulative and payout fixed deposits from a versioned rate card, pays periodic interest, deducts TDS, handles premature and partial withdrawals with penalty re-pricing, auto-renews on maturity, and gives loans against deposits. There is no UI: input is CSV and text files, and output is an event log.

> **Status:** work in progress. Core booking, payout, withdrawal, loan and maturity logic is implemented and covered by 13 rule tests. Scheduled jobs, backdated bookings, overdue renewal and printed reports are not yet implemented. See [Status](#status).

## Features

- Rate card lookup by tenure band and amount band, with SENIOR add-on and effective-date versioning (BR-01, BR-11)
- Bookings as CUMULATIVE, QUARTERLY_PAYOUT or MONTHLY_PAYOUT, with maturity instruction RENEW_PRINCIPAL, RENEW_ALL or PAYOUT (BR-03)
- Quarterly compounding on calendar quarter ends, with broken periods on actual days (BR-02)
- TDS per financial year with the Rs 40,000 / Rs 50,000 thresholds, 20% without PAN, and Form 15G/15H waiver (BR-04)
- Premature withdrawal re-priced at the run-tenure rate minus 1%, with no interest within 7 days (BR-05)
- Partial withdrawal in Rs 1,000 multiples, keeping at least Rs 5,000 (BR-06)
- Holiday-aware maturity, with extra days at the 3% savings rate (BR-07)
- Loan up to 90% of principal plus accrued interest, with a lien that blocks premature withdrawal (BR-08)
- Money kept at paisa precision, with payouts and TDS rounded to whole rupees (BR-10)

## Requirements

- Ubuntu (or any Linux/macOS with a shell)
- JDK 17 or newer

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
java -version
javac -version
```

No external libraries or build tools are needed.

## Quick start

```bash
git clone https://github.com/<your-user>/<repo>.git
cd <repo>
chmod +x run.sh
./run.sh
```

`run.sh` compiles the code, runs the tests, runs the engine on the sample commands, and prints the event log.

## Manual build and run

```bash
# compile
rm -rf out
javac -d out $(find src/main src/test -name "*.java")

# run tests (prints PASS/FAIL per rule, exits non-zero on failure)
java -cp out fd.FdTests

# run the engine
mkdir -p output
java -cp out fd.Main data/ratecard.csv data/holidays.csv data/commands.txt output/
cat output/events.log
```

## Usage

```
java -cp out fd.Main <ratecard.csv> <holidays.csv> <commands.txt> <outdir>
```

The engine writes `events.log` to `<outdir>`.

### Input files

**Rate card** (`ratecard.csv`): one band per line, grouped by effective date.

```
effective_from,min_days,max_days,amount_min,amount_max,rate,senior_addon,senior_min_days
2026-01-01,7,45,5000,20000000,4.00,0.50,180
```

**Holidays** (`holidays.csv`): one date per line, optionally followed by a name.

```
date,name
2026-08-15,Independence Day
```

**Commands** (`commands.txt`): one command per line. Lines starting with `#` are comments.

```
BOOK id customer TYPE MATURITY bookingDate tenure(7D|6M) amount senior(Y/N) pan(Y/N) form15(Y/N)
PAYOUT id periodEnd
PREMATURE_WITHDRAW id date
PARTIAL_WITHDRAW id date amount
LOAN_AGAINST_FD id date
MATURE id
```

Example:

```
BOOK FD1 C001 CUMULATIVE RENEW_ALL 2026-01-10 365D 100000 N Y N
PREMATURE_WITHDRAW FD1 2026-06-01
```

## Project layout

```
src/main/java/fd/
  Money.java       rounding helpers (paisa and whole rupee)
  RateCard.java    versioned rate card lookup
  Calendar.java    holidays, quarter ends, financial year
  Interest.java    cumulative compounding and simple interest
  Tds.java         per-customer, per-FY TDS
  Deposit.java     one FD record and its state
  Bank.java        booking, payout, withdrawals, loans, maturity, renewal
  Commands.java    command-line parser
  Main.java        CLI entry point
src/test/java/fd/
  FdTests.java     rule checks
data/              sample inputs (self-authored, not official)
docs/DESIGN.md     business rule register BR-01 to BR-11
run.sh             build, test and run in one step
```

## Business rules

Each rule is registered as BR-01 to BR-11 in [docs/DESIGN.md](docs/DESIGN.md), with its implementation location and status.

## Status

| Area | Status |
|------|--------|
| Rate card, versioning, senior add-on (BR-01) | Done, tested |
| Booking, minimums, month tenure (BR-03) | Done, tested |
| Partial withdrawal rules (BR-06) | Done, tested |
| Premature 7-day rule (BR-05) | Done, tested |
| Money rounding (BR-10) | Done |
| Cumulative and payout interest (BR-02) | Partial, not checked against official output |
| TDS thresholds and waiver (BR-04) | Partial |
| Premature re-pricing and excess interest recovery (BR-05) | Partial, recovery path not tested |
| Renewal and holiday maturity (BR-07) | Partial |
| Loan against FD (BR-08) | Partial |
| Overdue auto-renewal (BR-09) | Pending, method exists but is not called |
| Backdated booking (BR-11) | Pending |
| Scheduled daily/monthly job | Pending |
| FD advice, interest certificate, TDS statement | Pending |
| Stretch goals (sweep-in, tax-saver FD) | Not started |

Expected outputs from the official sample data have not been compared yet. Results from the bundled sample data are a smoke test only.

## Interpretations

These are assumptions made from the problem statement. They should be confirmed against the official expected outputs.

- "Within 7 days" is treated as 7 days or fewer, so day 7 earns no interest.
- Loan interest accrues at the FD's booking rate.
- Renewals use the rate card effective on the renewal date.

## Tests

`FdTests` runs 13 checks covering senior add-on boundaries, rate card versioning, minimum amount and tenure, month-end tenure, quarter ends, partial withdrawal rules, the 7-day rule, and blocking operations on closed and lien-marked deposits.

```bash
java -cp out fd.FdTests
```

## License

Add a license before publishing, for example MIT.
