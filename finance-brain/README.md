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

## Automatic updates

Every push that touches `finance-brain/` builds a signed APK and publishes it as a GitHub
Release tagged `fb-v<version>.<build>`. The app checks GitHub on launch, shows an
**Update available** card on the Home screen, downloads the APK, and hands it to the
Android installer. No Play Store involved.

One-time setup, because all builds must be signed with the same key:

1. Actions tab → **Finance Brain · generate signing key** → Run workflow.
2. Open the finished run. The summary shows the password; the artifact holds the key.
3. Settings → Secrets and variables → Actions → add
   `FINANCE_BRAIN_KEYSTORE_PASSWORD` and `FINANCE_BRAIN_KEYSTORE_BASE64`
   (contents of `keystore-base64.txt`).
4. Re-run **Finance Brain · build and release**. The first release appears under Releases.

## Install on your phone

1. Download `finance-brain.apk` from the latest GitHub Release.
2. Copy it to the phone and open it. Allow "install from unknown sources" if asked.
3. Open Finance Brain and tap **Allow SMS access**. The history scan starts at once.
4. When the first update arrives, Android asks once to let Finance Brain install apps.

The app is side-loaded on purpose. Google Play does not allow SMS permissions for an
app like this, and it is only for your own phone. A build made without the signing
secrets (a local `./gradlew assembleDebug`, or an APK from before the key existed)
cannot be updated over; uninstall it and install the first release once.

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
