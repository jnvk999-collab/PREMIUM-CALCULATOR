/**
 * Emails a merged PDF. Uses Gmail SMTP with an App Password by default
 * (Google account > Security > 2-Step Verification > App passwords), or any
 * SMTP server via SMTP_HOST / SMTP_PORT.
 *
 * .env:
 *   EMAIL_TO=you@gmail.com            (comma-separated for several)
 *   EMAIL_FROM=yourgmail@gmail.com
 *   EMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
 *   SMTP_HOST=smtp.gmail.com  SMTP_PORT=465   (optional, these are the defaults)
 */
const nodemailer = require('nodemailer');

function config() {
  const to = (process.env.EMAIL_TO || '').split(',').map(s => s.trim()).filter(Boolean);
  const from = process.env.EMAIL_FROM || '';
  const pass = (process.env.EMAIL_APP_PASSWORD || '').replace(/\s+/g, '');
  if (!to.length || !from || !pass) return null;
  return {
    to, from,
    host: process.env.SMTP_HOST || 'smtp.gmail.com',
    port: parseInt(process.env.SMTP_PORT || '465', 10),
    pass,
  };
}

let transport = null;
function getTransport(c) {
  if (transport) return transport;
  transport = nodemailer.createTransport({ host: c.host, port: c.port, secure: c.port === 465, auth: { user: c.from, pass: c.pass } });
  return transport;
}

const enabled = () => !!config();

async function sendPdf({ file, filename, subject, text }) {
  const c = config();
  if (!c) throw new Error('email not configured (EMAIL_TO / EMAIL_FROM / EMAIL_APP_PASSWORD)');
  await getTransport(c).sendMail({
    from: `"WhatsApp Bot" <${c.from}>`, to: c.to.join(', '), subject, text,
    attachments: [{ filename, path: file, contentType: 'application/pdf' }],
  });
}

async function verify() { const c = config(); if (!c) return false; await getTransport(c).verify(); return true; }

module.exports = { enabled, sendPdf, verify };
