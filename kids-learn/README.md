# Little Learners — K-5 practice companion

A small offline app for kindergarten to class 5 that pairs with the
[Infinity Learn K5 app](https://play.google.com/store/apps/details?id=com.infinitylearn.k5app).
Infinity Learn is strong on **watching and listening** (videos, rhymes, phonics, art).
Little Learners adds the part it is weak on: **daily hands-on practice with instant
feedback**, plus a parent dashboard that shows exactly where the child is strong or weak.

Same build style as the premium calculator: one `index.html`, works with no signal
once installed, no account, no ads, nothing leaves the phone.

## What the child gets

| Tile | What it does |
|---|---|
| 📅 **Daily challenge** | 10 mixed questions (6 maths + 4 spelling), once a day, 30 bonus stars. Builds the habit. |
| 🧮 **Maths** | 10-question rounds per skill. Skills change by class: counting and comparing (KG) up to long division, fractions, decimals and word problems (class 5). Wrong answers are shown again at the end. |
| 🐝 **Spelling** | *Listen & spell*: the phone reads the word aloud, the child types it. *Unscramble*: put the letters in order. 25 words per class, 150 total. |
| 📚 **Reading** | Short passages with a "Read to me" button and 3 comprehension questions. Two per class, from "The Red Ball" (KG) to "The Chandrayaan Landing" (class 5). |
| ✖️ **Times tables** | 60-second beat-the-clock for any table 2 to 12, or mixed. Best scores are kept. |
| 🏅 **Rewards** | Stars for every correct answer, 11 badges (first round, 3-day streak, spelling bee, tables master…). |

Any tile can be switched to a class above or below, so a child who has mastered
their own class can be stretched without changing the profile.

## What the parent gets

Tap **Parent** in the bottom bar (optionally locked with a 4-digit PIN).

- Minutes this week, day streak, and a 28-day calendar of practice days.
- Accuracy and time per activity.
- **What to work on**: any maths skill under 60% after 10+ answers is flagged; anything over 90% is marked as mastered with a nudge to try the next class.
- Recent rounds, export/import of progress as a JSON file (to move phones), and a full reset.

## Install on the phone

Host the six files in this folder on GitHub Pages (or any static host), the same
way as the calculator, then:

- **Android**: open the link in Chrome → ⋮ → **Install app**.
- **iPhone**: open in Safari → Share → **Add to Home Screen**.

If this repo is already published on GitHub Pages, the app is at
`<your-pages-url>/kids-learn/`.

## Updating

1. Upload the new `index.html`.
2. Bump the cache name in `sw.js` (`little-learners-v1` → `v2`). Without this, phones keep the old version.

## A weekly plan that actually works (15 minutes a day)

Research on young learners is consistent: short, daily, active practice with
immediate feedback beats long passive sessions. Use both apps like this:

| Day | Infinity Learn (5–10 min) | Little Learners (10 min) |
|---|---|---|
| Mon | A "Let's Learn" maths video | Daily challenge + one Maths round on the same topic |
| Tue | A rhyme or phonics activity | Daily challenge + Spelling (Listen & spell) |
| Wed | A story from "Let's read & recite" | Daily challenge + one Reading passage, child reads aloud to you |
| Thu | A "Let's create" art activity | Daily challenge + Times tables (try to beat yesterday's score) |
| Fri | Free choice | Daily challenge + whatever the Parent area flags as "needs practice" |
| Weekend | Off screens: board game, cooking (measuring), reading a real book | Optional: one round of the child's favourite tile |

Habits that make the biggest difference:

1. **Same time every day** (after snack, before play). The streak counter does the nagging for you.
2. **Sit beside them for the first week.** After that, let them run it alone and just check the Parent area on Sunday.
3. **Praise effort, not stars.** "You kept going after three wrong ones" beats "you got 10/10".
4. **Say sums out loud.** Talking through "7 plus 8… 7 plus 3 is 10, plus 5 is 15" builds number sense faster than tapping.
5. **Let them teach you.** After a reading passage, ask the child to explain it to you in their own words.
6. **Read real books too.** Twenty minutes of bedtime reading (to them or by them) is the single strongest predictor of school success. No app replaces it.
7. **Screens off an hour before bed.** Sleep is when the day's learning is stored.

## Ideas for the next version

- Hindi and regional-language spelling lists.
- Hand-writing practice using the touch screen (letter tracing).
- A "parent adds words" list for the school's weekly spelling test.
- Share a weekly report card as a WhatsApp image, reusing the bot in `whatsapp-bot/`.
