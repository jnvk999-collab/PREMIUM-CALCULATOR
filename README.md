# OIC Premium Calculator

Offline-capable premium calculator for The Oriental Insurance Company Ltd.

**Build v26** · 25-Jul-2026

## Products

| Line | Products |
|---|---|
| **Motor** | Two-wheeler, Two-wheeler Hire & Reward, Private Car, GCCV, PCCV (bus/taxi/auto), Misc Class D — with CR-8811 commission |
| **Health** | Sampoorna Swasthya Suraksha (OSSS), Mediclaim Individual 2024, Mediclaim Group 2024, Happy Family Floater 2024, Overseas Mediclaim (Business & Holiday), Youth Eco Care |
| **Fire** | Fire & Engineering premium calculator |

## Files — upload all six, at the top level

| File | Purpose |
|---|---|
| `index.html` | The whole application. **Name must not change** — GitHub Pages looks for it. |
| `sw.js` | Service worker; makes the app work offline. |
| `manifest.json` | App name, icon and colours for Add to Home Screen. |
| `icon-180.png` | Home-screen icon (iPhone) |
| `icon-192.png` | Home-screen icon (Android) |
| `icon-512.png` | Splash screen |

## Built-in self-test

Tap the small **build stamp** in the bottom-right corner.

It runs **700 regression cases** (every vehicle class × slab × zone × age × IDV × fuel × policy type) plus **9 document-verified checks** whose expected values come from OIC worksheets and circulars, not from the calculator itself.

- Green = nothing has moved since the baseline.
- Red = it names each case and which field changed.

The suite resets all state before every case, so it gives the same answer whether the app was just opened or you have been quoting all morning.

**Run it after every rate change.** If a circular should have affected 12 cases and 200 changed, something else broke.

## Updating

1. Upload the new `index.html`.
2. **Edit `sw.js` and change the cache version** — `oic-calc-v26` becomes `oic-calc-v27`.

Step 2 is not optional. Without it, phones keep serving the cached old version.

Then: close the app fully (swipe up in the app switcher), open it, close it fully again, open again. The second open loads the new build. Confirm the build stamp changed.

## Installing on a phone

**iPhone** — open the Pages link in **Safari** (not Chrome), let it load fully on Wi-Fi, tap **Share** → **Add to Home Screen**.

**Android** — open in **Chrome**, tap **⋮** → **Install app**.

iOS will not run a downloaded `.html` file from the Files app — JavaScript is blocked there. It must be opened from the hosted link.

## Known limitations

- **OD rate band uses age at policy expiry**, not at inception. Three OICL spreadsheets use inception age. Retained deliberately; unresolved against INLIAS.
- **Fire module is unaudited** and not covered by the regression suite.
- The 2024 health products and Overseas Mediclaim are verified against their premium charts, not against INLIAS.
- Overseas Mediclaim: the chart gives no upper limit for SI increments, and does not state whether the Staff (33%) and Digital (5%) discounts may be combined — the tool allows both and applies Staff first.
- No warning when a typed OD discount exceeds the OO authority cap (the Nil-Dep cap does warn).
- ₹2 crore IDV ceilings and RO/HO referral limits are not modelled.
- Battery / Engine Protect 5-year clock runs from registration date; the circular says invoice date or first registration, whichever is earlier.
- Agriculture Tractor >6 HP OD and TP rates are inherited from the ≤6 HP row and unverified.

## Note on hosting

A public GitHub Pages repo means this file is readable by anyone with the link. No customer data is stored or transmitted — everything stays on the device. But it does contain OIC rate tables, the 01/06/2026 discount structure and CR-8811 commission rates. Check with your DO or RO before publishing publicly; the same six files work on any internal web host.
