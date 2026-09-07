@echo off
cd /d "%~dp0"
title OIC WhatsApp bot
:loop
node bot.js
echo.
echo Bot stopped. Restarting in 10 seconds (close this window to stop it)...
timeout /t 10 >nul
goto loop
