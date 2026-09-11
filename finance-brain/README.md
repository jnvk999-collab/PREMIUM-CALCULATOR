# Finance Brain

Automatic personal money tracker for Android. It reads bank and UPI alert SMS from
SBI, HDFC, ICICI, Federal Bank and PhonePe, including the messages already on the
phone, and builds a complete ledger with balances, categories and insights. No typing.

## What it does today (milestone 1 + 2)

- **Automatic capture.** A broadcast receiver adds every new bank alert within a second.
  On first launch the whole SMS inbox is scanned so past history is included.
- **Parsers** for SBI, HDFC, ICICI, Federal Bank, PhonePe/UPI handles, cards and ATM,
  with OTP, promo, reminder and failed-transaction messages filtered out.
- **Smart categories.** Keyword rules for Indian merchants (Swiggy, Zomato, Amazon,
  Jio, BESCOM, Zerodha and many more). Correct a category once and the app remembers
  that merchant. Transfers between your own accounts are not counted as spending.
- **Home screen with everything visible.** Total balance across accounts, this month's
  income / spent / saved, per-account balances, spending by category, daily spend
  chart, upcoming recurring payments and recent activity in one scroll.
- **Activity** tab with instant search and filters, grouped by day.
- **Insights** with six-month income vs spending, savings rate, top merchants and
  payment methods.
- **Manual cash entry** from the + button.
- **Private by design.** Everything lives in a local Room database. Nothing is uploaded.

## Install on your phone

1. Download `app-debug.apk` from the latest GitHub Actions run (Actions tab → Build
   Finance Brain APK → Artifacts), or build it locally with `./gradlew assembleDebug`.
2. Copy it to the phone and open it. Allow "install from unknown sources" if asked.
3. Open Finance Brain and tap **Allow SMS access**. The history scan starts at once.

The app is side-loaded on purpose. Google Play does not allow SMS permissions for an
app like this, and it is only for your own phone.

## Project layout

```
app/src/main/java/com/financebrain
├── parser/      BankSmsParser (SMS → transaction), Categorizer
├── data/        Room entities, DAOs, TransactionRepository, Insights
├── sms/         SmsReceiver (live), SmsInboxScanner (history)
└── ui/          Compose screens: Home, Transactions, Insights, Settings, sheets
```

Unit tests for the parsers live in `app/src/test`. Run `./gradlew testDebugUnitTest`.

## Roadmap

3. Server with Gmail sync and cross-source dedup
4. Bank statement and PhonePe statement PDF import for older history
5. Alerts, budgets and an ask-a-question chat over your data
