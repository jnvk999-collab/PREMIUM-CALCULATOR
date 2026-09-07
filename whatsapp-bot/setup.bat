@echo off
cd /d "%~dp0"
echo === OIC WhatsApp bot: one-time setup ===
where node >nul 2>nul || (echo Node.js is not installed. Install it from https://nodejs.org (LTS) and run this again. & pause & exit /b 1)
call npm install
call npx puppeteer browsers install chrome
call npx playwright install chromium
if not exist .env copy .env.example .env
echo.
echo Running self-test (should end with ALL OK)...
call npm test
echo.
echo Setup finished. Now open .env in Notepad, fill in your name and allowed numbers,
echo then double-click start.bat
pause
