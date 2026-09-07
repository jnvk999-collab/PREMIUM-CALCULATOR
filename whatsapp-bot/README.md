# WhatsApp automation for the OIC Premium Calculator

Turns the calculator in the parent folder into a WhatsApp auto-responder.

| Someone sends… | The bot does… |
|---|---|
| Photos (RC, Aadhaar, old policy, vehicle pics) | Saves them under `inbox/<contact>/<date>/`, waits for the burst to finish (45 s, or the word **pdf**), merges them into one A4 PDF and sends it back |
| `quote bike 125cc 2021 idv 60000` | Parses the line, prices it with the **real calculator** (`../index.html` running in headless Chromium), replies with a premium breakdown **and** the calculator's own quote PDF |
| `quote car 1200cc reg 2019 idv 4.5 lakh zone A ncb 25 zero dep diesel for Ramesh Kumar` | Same, with name, NCB, zone, fuel and add-on picked up |
| `tp only activa 2018` | Third-party-only quote (no IDV needed) |
| `quote please` | Replies listing exactly what is missing (vehicle, CC, year, IDV) |
| `help` | Usage text |
| Anything else | Logged to `inbox/log.jsonl`, no reply, so you handle it yourself |

Every incoming file and every outgoing PDF is written to `inbox/`, so the inbox folder becomes your organised archive, one folder per contact per day, plus a one-line-per-event JSON log you can search.

## How it fits together

```
WhatsApp (your number, linked like WhatsApp Web)
        │  whatsapp-web.js
        ▼
     bot.js ─── photo ───▶ lib/organise.js  (save)  ──▶ lib/pdfMerge.js (pdf-lib) ──▶ reply with PDF
        │
        └──── text ─────▶ lib/intents.js  (parse) ──▶ lib/calculator.js ──▶ lib/reply.js ──▶ reply + PDF
                                                          │
                                                headless Chromium running ../index.html
                                                calls calculate() + generatePDF() exactly
                                                like the built-in self-test does
```

**Which calculator does it price with?** In this order: the `CALCULATOR_HTML` setting in `.env` (a file path or a URL such as `https://nvkoicl.github.io/PREMIUM-CALCULATOR/`), otherwise `../index.html` if the bot folder sits inside a calculator folder, otherwise the published site above. Verified against build v82 of the published calculator: the smoke test passes with it unchanged.

The important design choice: **the bot does not re-implement any rating logic.** It opens `index.html` once in headless Chromium and drives it the same way the calculator's own 700-case regression suite does. When you upload a new `index.html` with new rates, the bot prices with the new rates on its next quote. The smoke test proves this by checking one of the calculator's golden cases (₹1,915) through the bot's driver.

## Setup (on the PC or small server that will stay on)

**Windows:** double-click `setup.bat` once (installs everything, runs the self-test, creates `.env`). Edit `.env` in Notepad. Then double-click `start.bat` and scan the QR (also saved as `qr.png` in this folder).

**Mac / Linux:** run `./setup.sh` once, edit `.env`, then `./start.sh`.

By hand, the same thing is:

```bash
cd whatsapp-bot
npm install
npx puppeteer browsers install chrome     # browser for the WhatsApp link (one time)
npx playwright install chromium           # browser for the calculator (one time)
cp .env.example .env                      # then edit AGENT_NAME etc.
npm test                                  # should end with ALL OK
npm start                                 # scan the QR with WhatsApp > Linked devices
```

Keep it running with `pm2 start bot.js --name oic-bot` or a systemd unit. The QR scan is needed once; the session is kept in `.wwebjs_auth/`.

Settings (`.env`):

| Key | Meaning | Default |
|---|---|---|
| `AGENT_NAME` | Signature under every quote | empty |
| `MERGE_WAIT_SECONDS` | Quiet period before photos are merged | 45 |
| `REPLY_IN_GROUPS` | `1` to also respond inside groups | 0 |
| `ALLOW_NUMBERS` | Comma-separated numbers to respond to; empty = everyone | empty |
| `AUTO_QUOTE` / `AUTO_PDF` | `0` to switch either feature off | 1 |
| `INBOX_DIR` | Where files are stored | `./inbox` |

## Which WhatsApp connection to use

**This prototype uses `whatsapp-web.js`** because it works with the number you already use: customers keep messaging you, the bot answers from the same chat. It is the only option where "someone sends me photos" reaches the bot without changing your number.

Be aware:
- It is an unofficial library that automates WhatsApp Web. Meta can ban numbers that look automated. Start with `ALLOW_NUMBERS` set to a few known contacts, keep replies human-paced (the bot already replies once per burst, not per photo), and consider a **separate SIM** for the bot once you trust it.
- It needs a machine that stays on with a browser (any laptop, a ₹3,000 mini PC, or a small cloud VM). Phone must stay connected to the internet.

**The official route** is the WhatsApp Business Cloud API (Meta). No ban risk, proper webhooks, template messages. Costs per conversation, needs a Meta Business verification and a number that is **not** on the regular WhatsApp app. The code here is split so only `bot.js` (about 100 lines) would change: `lib/` is transport-independent. If you go official, `bot.js` becomes an Express webhook that downloads media from Meta's URL and posts replies to the Graph API.

## What the parser understands

- **Vehicle**: bike/scooty/activa/… → two-wheeler; car/swift/creta/… → private car; tempo/truck/pickup → goods carrier; auto/taxi/bus → passenger (PCCV needs the class, which the parser cannot guess yet, so it asks you to use the app).
- **Slab**: `125cc`, `1200 cc`, `7 kw` (electric), `2500 kg` / `12 ton` for goods.
- **Year**: `2021`, `03/2019`, `15-03-2019`. A bare year is taken as June of that year.
- **IDV**: `idv 60000`, `4.5 lakh`, `60k`, `Rs 4,50,000`.
- **Policy**: `tp only` / `third party`, `od only`, otherwise comprehensive.
- **Zone**: `zone A`, or a metro name; default B.
- **NCB**: `ncb 25`, `25% ncb`, `ncb 2 years`.
- **Add-ons**: `zero dep` / `nil dep`, `engine protect`, `rsa`, `imt 23`.
- **PDF fields**: `for Ramesh Kumar`, registration `TS09 AB 1234`, common model names.

Health and fire products are not wired yet. The calculator exposes `yecCalculate()`, `ohGeneratePDF()` and the fire `calculate()` the same way, so adding them is another driver function in `lib/calculator.js` plus a few parser rules.

## Safety rails already in

- Never replies to its own messages, statuses, or (by default) groups.
- Replies **once** when a photo burst starts, once with the PDF. No per-photo chatter.
- A quote reply always carries "indicative, subject to underwriting".
- If the calculator rejects the input (future date, missing IDV), the bot says what is wrong instead of sending a wrong number.
- Everything is logged; nothing is deleted from the phone.

## Test

`npm test` runs without WhatsApp: golden-case pricing, validation error path, parser cases, an end-to-end car quote, and a 3-image merge with one corrupt file skipped.
