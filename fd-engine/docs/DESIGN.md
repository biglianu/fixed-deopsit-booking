# PS-018 Design Notes

## Business rule register

| ID    | Rule | Implementation |
|-------|------|----------------|
| BR-01 | Rate card by tenure and amount band; version by effective date; SENIOR +0.5% for tenure >= 180 days | `RateCard.rate`, `RateCard.cardOn` |
| BR-02 | Cumulative: quarterly compounding on calendar quarter ends; broken first/last periods on actual days; payouts simple interest; monthly payout uses 0.9975 x quarterly-equivalent rate | `Interest.cumulativeMaturity`, `Interest.simple`, `Interest.MONTHLY_DISCOUNT` |
| BR-03 | Min Rs 5,000; min tenure 7 days; max 10 years; month tenure keeps day-of-month, clamped to month end | `Bank.book`, `Bank.addMonthsSameDay` |
| BR-04 | TDS 10% (20% no PAN) above FY threshold Rs 40,000 (Rs 50,000 SENIOR); Form 15G/H waives | `Tds.credit` |
| BR-05 | Premature: re-rate on booking card for run tenure minus 1%; no penalty and no interest within 7 days; excess paid interest recovered from principal | `Bank.prematureWithdraw` |
| BR-06 | Partial: multiples of Rs 1,000; remaining >= Rs 5,000; slice closed prematurely; rest keeps rate and maturity | `Bank.partialWithdraw` |
| BR-07 | Maturity on holiday: extra days at 3% savings rate; RENEW_ALL / RENEW_PRINCIPAL / PAYOUT | `Bank.mature`, `Bank.renew` |
| BR-08 | Loan up to 90% of principal plus accrued interest at FD rate; loan rate FD+2%; lien blocks premature withdrawal | `Bank.loanAgainst` |
| BR-09 | Overdue PAYOUT with no claim for 14 days auto-renews | `Bank.applyOverdueRenewal` (NOT YET WIRED INTO COMMANDS) |
| BR-10 | Accrual in paisa, payout rounded to rupee, TDS rounded to rupee | `Money` |
| BR-11 | BACKDATE up to 3 days uses card for value date | NOT IMPLEMENTED |

## Interpretations made (confirm against Week 2 expected outputs)
- "Within 7 days" is read as held <= 7 days (day 7 has no interest).
- Loan accrued interest is computed at the FD booking rate (BR-08), not run-tenure rate.
- Renewals reuse the card effective on the renewal date (BR-07).

## Known gaps
- BR-09 overdue auto-renewal is not triggered by the command file.
- BR-11 backdated booking is not implemented.
- Print of FD advice, interest certificate and TDS statement by financial year (requirement 6) is not implemented.
- Monthly payout schedule is driven only by explicit PAYOUT commands; no daily/monthly job runner.
- Sweep-in and tax-saver FD stretch goals are not implemented.
- Sample data is self-authored; official Week 2 data should be used for final checks.
