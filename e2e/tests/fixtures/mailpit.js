const { setTimeout: sleep } = require('node:timers/promises');

const apiUrl = process.env.MAILPIT_API_URL || 'http://127.0.0.1:18025/api/v1';

async function mailpitJson(path) {
  const response = await fetch(`${apiUrl}${path}`);
  if (!response.ok) throw new Error(`Mailpit API ${response.status}: ${path}`);
  return response.json();
}

async function latestMessages() {
  const data = await mailpitJson('/messages?limit=50');
  return data.messages || data.Messages || [];
}

async function waitForVerificationCode({ email, timeoutMs = 30_000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const message of await latestMessages()) {
      const recipients = JSON.stringify(message.To || message.to || '');
      if (email && !recipients.includes(email)) continue;
      const detail = await mailpitJson(`/message/${message.ID || message.id}`);
      const content = [detail.Text, detail.HTML, detail.text, detail.html, message.Subject].filter(Boolean).join('\n');
      const match = content.match(/\b(\d{6})\b/);
      if (match) return match[1];
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for a verification code${email ? ` sent to ${email}` : ''}`);
}

async function clearMailpit() {
  const response = await fetch(`${apiUrl}/messages`, { method: 'DELETE' });
  if (!response.ok && response.status !== 404) throw new Error(`Mailpit cleanup failed: HTTP ${response.status}`);
}

module.exports = { clearMailpit, latestMessages, waitForVerificationCode };
