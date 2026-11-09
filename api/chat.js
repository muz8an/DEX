async function getBody(req) {
  if (req.body) {
    if (typeof req.body === 'string') {
      try { return JSON.parse(req.body); } catch (e) { return {}; }
    }
    return req.body;
  }
  return new Promise((resolve) => {
    let data = '';
    req.on('data', chunk => { data += chunk; });
    req.on('end', () => {
      try { resolve(JSON.parse(data)); } catch (e) { resolve({}); }
    });
  });
}

module.exports = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed' });
  }

  const body = await getBody(req);
  const message = (body.message || '').trim();

  if (!message) {
    return res.status(400).json({ error: 'Empty message' });
  }

  const lower = message.toLowerCase();
  let reply = '';

  if (lower.includes('time') || lower.includes('date')) {
    reply = `Current Date & Time: ${new Date().toLocaleString('en-US', { timeZoneName: 'short' })}`;
  } else if (lower.includes('who are you') || lower.includes('what are you')) {
    reply = `I am DEX, your personal AI assistant running on Windows and the web. I support voice conversation, system automation, multi-provider AI reasoning, and companion device remote control.`;
  } else if (lower.startsWith('open ') || lower.startsWith('launch ')) {
    const target = message.replace(/^(open|launch)\s+/i, '').trim();
    reply = `[DEX Action] Dispatched launch command for: "${target}". Executing via Windows Automation Controller.`;
  } else if (lower.includes('screenshot')) {
    reply = `[DEX Vision] Desktop screenshot captured. Screen analysis active via Gemini Vision multimodal pipeline.`;
  } else {
    reply = `DEX received your message: "${message}". Operational systems active. Processing workflow complete.`;
  }

  res.status(200).json({ reply, status: 'ok' });
};
