#!/bin/bash
# One-command install on a fresh Ubuntu 22.04/24.04 server:
#   curl -fsSL https://raw.githubusercontent.com/jnvk999-collab/PREMIUM-CALCULATOR/claude/whatsapp-photo-pdf-automation-mgr9gu/whatsapp-bot/cloud-setup.sh | sudo bash
# Installs Node.js, the bot, a systemd service that keeps it running (and
# auto-updating), asks for your settings, and prints the private status link
# where you scan the QR.
set -e
BRANCH="claude/whatsapp-photo-pdf-automation-mgr9gu"
REPO="https://github.com/jnvk999-collab/PREMIUM-CALCULATOR.git"
BOT_USER="bot"
DIR="/home/$BOT_USER/whatsapp-bot"

echo "== 1/5 Installing Node.js and tools =="
apt-get update -qq
apt-get install -y -qq curl git ca-certificates >/dev/null
if ! command -v node >/dev/null || [ "$(node -v | cut -c2-3)" -lt 20 ]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash - >/dev/null
  apt-get install -y -qq nodejs >/dev/null
fi
echo "   node $(node -v)"

echo "== 2/5 Getting the bot =="
id -u $BOT_USER >/dev/null 2>&1 || useradd -m -s /bin/bash $BOT_USER
if [ -d "$DIR" ]; then
  sudo -u $BOT_USER git -C "$DIR/.." pull -q || true
else
  sudo -u $BOT_USER git clone -q --depth 1 -b "$BRANCH" "$REPO" "/home/$BOT_USER/repo"
  ln -s "/home/$BOT_USER/repo/whatsapp-bot" "$DIR"
fi
cd "$DIR"
sudo -u $BOT_USER npm install --no-audit --no-fund --silent

echo "== 3/5 Your settings =="
if [ ! -f .env ]; then
  read -rp "Your name (shown on the cover page): " AGENT_NAME </dev/tty
  read -rp "Email address to receive PDFs (blank = no email): " EMAIL_TO </dev/tty
  EMAIL_FROM=""; EMAIL_APP_PASSWORD=""
  if [ -n "$EMAIL_TO" ]; then
    read -rp "Gmail address to send FROM: " EMAIL_FROM </dev/tty
    read -rp "Gmail App Password (16 letters): " EMAIL_APP_PASSWORD </dev/tty
  fi
  TOKEN=$(head -c 24 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 24)
  cat > .env <<ENV
AGENT_NAME=$AGENT_NAME
MERGE_WAIT_SECONDS=45
ALLOW_NUMBERS=
REPLY_IN_GROUPS=1
ALLOW_GROUPS=
AUTO_QUOTE=0
AUTO_PDF=1
PDF_TO=me
OCR_VEHICLE=1
EMAIL_TO=$EMAIL_TO
EMAIL_FROM=$EMAIL_FROM
EMAIL_APP_PASSWORD=$EMAIL_APP_PASSWORD
UPDATE_CHECK_MINUTES=30
AUTO_UPDATE=1
STATUS_PORT=8080
STATUS_TOKEN=$TOKEN
ENV
  chown $BOT_USER:$BOT_USER .env; chmod 600 .env
fi
TOKEN=$(grep '^STATUS_TOKEN=' .env | cut -d= -f2)

echo "== 4/5 Installing the always-on service =="
cat > /etc/systemd/system/oic-bot.service <<UNIT
[Unit]
Description=OIC WhatsApp bot
After=network-online.target
Wants=network-online.target

[Service]
User=$BOT_USER
WorkingDirectory=$DIR
ExecStart=$(command -v node) $DIR/run.js
Restart=always
RestartSec=5
Environment=NODE_ENV=production

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable -q oic-bot
systemctl restart oic-bot

echo "== 5/5 Firewall =="
if command -v ufw >/dev/null; then ufw allow 8080/tcp >/dev/null 2>&1 || true; fi

IP=$(curl -fsS -4 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
echo
echo "======================================================================"
echo " Done. Open this PRIVATE link in any browser to scan the QR and watch:"
echo "   http://$IP:8080/$TOKEN/"
echo " Keep it secret: anyone with it can link your WhatsApp."
echo
echo " Useful commands on this server:"
echo "   systemctl status oic-bot        # running?"
echo "   journalctl -u oic-bot -f        # live log"
echo "   systemctl restart oic-bot       # restart"
echo "======================================================================"
