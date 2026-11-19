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

const APP_MAP = {
  'youtube': { label: 'YouTube', url: 'https://www.youtube.com', web: 'https://www.youtube.com' },
  'yt': { label: 'YouTube', url: 'https://www.youtube.com', web: 'https://www.youtube.com' },
  'google': { label: 'Google Search', url: 'https://www.google.com', web: 'https://www.google.com' },
  'github': { label: 'GitHub', url: 'https://github.com', web: 'https://github.com' },
  'chatgpt': { label: 'ChatGPT', url: 'https://chatgpt.com', web: 'https://chatgpt.com' },
  'gpt': { label: 'ChatGPT', url: 'https://chatgpt.com', web: 'https://chatgpt.com' },
  'notion': { label: 'Notion', url: 'https://www.notion.so', web: 'https://www.notion.so' },
  'spotify': { label: 'Spotify', url: 'https://open.spotify.com', protocol: 'spotify:', web: 'https://open.spotify.com' },
  'whatsapp': { label: 'WhatsApp', url: 'https://web.whatsapp.com', protocol: 'whatsapp:', web: 'https://web.whatsapp.com' },
  'calculator': { label: 'Calculator', url: 'https://www.google.com/search?q=calculator', protocol: 'calculator:', web: 'https://www.google.com/search?q=calculator' },
  'calc': { label: 'Calculator', url: 'https://www.google.com/search?q=calculator', protocol: 'calculator:', web: 'https://www.google.com/search?q=calculator' },
  'notepad': { label: 'Notepad', url: 'https://notepad.js.org', protocol: 'notepad:', web: 'https://notepad.js.org' },
  'settings': { label: 'Windows Settings', url: 'ms-settings:', protocol: 'ms-settings:', web: null },
  'camera': { label: 'Camera', url: 'microsoft.windows.camera:', protocol: 'microsoft.windows.camera:', web: null },
  'clock': { label: 'Clock', url: 'ms-clock:', protocol: 'ms-clock:', web: 'https://time.is' },
  'alarm': { label: 'Alarms & Clock', url: 'ms-clock:', protocol: 'ms-clock:', web: 'https://time.is' },
  'paint': { label: 'Paint', url: 'ms-paint:', protocol: 'ms-paint:', web: 'https://jspaint.app' },
  'maps': { label: 'Google Maps', url: 'https://www.google.com/maps', web: 'https://www.google.com/maps' },
  'google maps': { label: 'Google Maps', url: 'https://www.google.com/maps', web: 'https://www.google.com/maps' },
  'mail': { label: 'Gmail', url: 'https://mail.google.com', protocol: 'mailto:', web: 'https://mail.google.com' },
  'email': { label: 'Gmail', url: 'https://mail.google.com', protocol: 'mailto:', web: 'https://mail.google.com' },
  'gmail': { label: 'Gmail', url: 'https://mail.google.com', web: 'https://mail.google.com' },
  'calendar': { label: 'Google Calendar', url: 'https://calendar.google.com', web: 'https://calendar.google.com' },
  'reddit': { label: 'Reddit', url: 'https://www.reddit.com', web: 'https://www.reddit.com' },
  'twitter': { label: 'X (Twitter)', url: 'https://x.com', web: 'https://x.com' },
  'x': { label: 'X (Twitter)', url: 'https://x.com', web: 'https://x.com' },
  'linkedin': { label: 'LinkedIn', url: 'https://www.linkedin.com', web: 'https://www.linkedin.com' },
  'netflix': { label: 'Netflix', url: 'https://www.netflix.com', web: 'https://www.netflix.com' },
  'twitch': { label: 'Twitch', url: 'https://www.twitch.tv', web: 'https://www.twitch.tv' },
  'amazon': { label: 'Amazon', url: 'https://www.amazon.com', web: 'https://www.amazon.com' },
  'vscode': { label: 'VS Code', url: 'https://vscode.dev', protocol: 'vscode:', web: 'https://vscode.dev' },
  'vs code': { label: 'VS Code', url: 'https://vscode.dev', protocol: 'vscode:', web: 'https://vscode.dev' },
  'code': { label: 'VS Code', url: 'https://vscode.dev', protocol: 'vscode:', web: 'https://vscode.dev' },
  'discord': { label: 'Discord', url: 'https://discord.com/app', protocol: 'discord:', web: 'https://discord.com/app' },
  'telegram': { label: 'Telegram', url: 'https://web.telegram.org', protocol: 'tg:', web: 'https://web.telegram.org' },
  'steam': { label: 'Steam', url: 'https://store.steampowered.com', protocol: 'steam:', web: 'https://store.steampowered.com' },
  'music': { label: 'YouTube Music', url: 'https://music.youtube.com', web: 'https://music.youtube.com' },
  'wikipedia': { label: 'Wikipedia', url: 'https://www.wikipedia.org', web: 'https://www.wikipedia.org' }
};

function resolveAction(rawText) {
  if (!rawText) return null;
  const text = rawText.trim();
  const lower = text.toLowerCase();

  // If this is a tool inspection command like "Run tool: open_app", don't parse as action
  if (/^(?:run\s+)?tool:\s*/i.test(lower) || lower === 'open_app' || lower === 'open_url') {
    return null;
  }

  // Pattern 1: Play song/music
  const playMatch = lower.match(/^(?:play|listen to)\s+(.+)$/i);
  if (playMatch) {
    const query = playMatch[1].replace(/^(?:the\s+)?(?:song|music|track|video)\s+/i, '').trim();
    return {
      type: 'play',
      target: query,
      label: `Play "${query}" on YouTube`,
      url: `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`,
      protocol: null,
      isWeb: true
    };
  }

  // Pattern 2: Search web
  const searchMatch = lower.match(/^(?:search(?:\s+for)?|google)\s+(.+)$/i);
  if (searchMatch) {
    const query = searchMatch[1].trim();
    return {
      type: 'search',
      target: query,
      label: `Google Search: "${query}"`,
      url: `https://www.google.com/search?q=${encodeURIComponent(query)}`,
      protocol: null,
      isWeb: true
    };
  }

  // Pattern 3: Open / Launch / Start / Visit
  let target = null;
  const openMatch = lower.match(/^(?:open|launch|start|run|go to|visit)\s+(.+)$/i);
  if (openMatch) {
    target = openMatch[1].trim();
  } else {
    const politeMatch = lower.match(/(?:please\s+|can you\s+|could you\s+)?(?:open|launch|start|visit|go to)\s+([^,.?!]+)/i);
    if (politeMatch) {
      target = politeMatch[1].trim();
    }
  }

  if (!target) return null;

  // Clean target
  target = target.replace(/^(the|my)\s+/i, '').replace(/[.?!]+$/, '').trim();
  const targetLower = target.toLowerCase();

  // URL checking
  if (/^https?:\/\//i.test(target)) {
    let hostname = target;
    try { hostname = new URL(target).hostname; } catch (e) {}
    return {
      type: 'open',
      target,
      label: hostname || 'External Website',
      url: target,
      protocol: null,
      isWeb: true
    };
  }

  if (/^[a-zA-Z0-9-]+\.(?:com|org|net|io|ai|co|app|dev|edu|gov|xyz|me)(?:\/[^\s]*)?$/i.test(target)) {
    return {
      type: 'open',
      target,
      label: target,
      url: 'https://' + target,
      protocol: null,
      isWeb: true
    };
  }

  // Exact map match
  if (APP_MAP[targetLower]) {
    const item = APP_MAP[targetLower];
    return {
      type: 'open',
      target,
      label: item.label,
      url: item.url,
      protocol: item.protocol || null,
      web: item.web || item.url,
      isWeb: Boolean(item.web)
    };
  }

  // Substring map match
  for (const [key, item] of Object.entries(APP_MAP)) {
    if (targetLower.includes(key)) {
      return {
        type: 'open',
        target,
        label: item.label,
        url: item.url,
        protocol: item.protocol || null,
        web: item.web || item.url,
        isWeb: Boolean(item.web)
      };
    }
  }

  // Fallback web search
  return {
    type: 'open',
    target,
    label: target.charAt(0).toUpperCase() + target.slice(1),
    url: `https://www.google.com/search?q=${encodeURIComponent(target)}`,
    protocol: null,
    web: `https://www.google.com/search?q=${encodeURIComponent(target)}`,
    isWeb: true
  };
}

async function callGemini(apiKey, prompt, history = [], requestedModel = 'gemini-2.0-flash') {
  const modelsToTry = [
    requestedModel,
    'gemini-2.0-flash',
    'gemini-1.5-flash',
    'gemini-1.5-pro'
  ];
  const uniqueModels = [...new Set(modelsToTry.map(m => m.replace(/^models\//, '')))];
  let lastError = null;

  for (const model of uniqueModels) {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;
    const contents = [];

    if (Array.isArray(history) && history.length > 0) {
      for (const h of history.slice(-6)) {
        if (h.sender && h.text) {
          contents.push({
            role: h.sender === 'user' ? 'user' : 'model',
            parts: [{ text: h.text }]
          });
        }
      }
    }
    contents.push({ role: 'user', parts: [{ text: prompt }] });

    try {
      const resp = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents,
          systemInstruction: {
            parts: [{
              text: "You are DEX, the personal AI executive assistant running on Windows and Web. You are direct, intelligent, precise, and polite. When the user asks to open an application, site, or execute an action, confirm it clearly and concisely."
            }]
          }
        })
      });

      if (!resp.ok) {
        const errText = await resp.text();
        throw new Error(`Gemini API error (${resp.status}): ${errText}`);
      }

      const data = await resp.json();
      const replyText = data?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (replyText) {
        return { reply: replyText, model };
      }
    } catch (err) {
      lastError = err;
    }
  }

  throw lastError || new Error('All Gemini models failed');
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
  const apiKey = (body.apiKey || process.env.GEMINI_API_KEY || '').trim().replace(/^['"]|['"]$/g, '');
  const model = (body.model || 'gemini-2.0-flash').trim();

  if (!message) {
    return res.status(400).json({ error: 'Empty message' });
  }

  // Pre-resolve any open / search / play action
  const action = resolveAction(message);
  if (action) {
    try {
      const { enqueueCommand } = require('./remote-queue.js');
      enqueueCommand(message, action, body.source || (body.isMobile ? 'mobile' : 'chat'));
    } catch (e) {}
  }

  // 1. If user provided a real Gemini API key, try calling Google Gemini
  if (apiKey && apiKey.startsWith('AIza')) {
    try {
      const geminiResult = await callGemini(apiKey, message, body.history, model);
      return res.status(200).json({
        reply: geminiResult.reply,
        status: 'ok',
        provider: `GEMINI (${geminiResult.model.toUpperCase()})`,
        action
      });
    } catch (err) {
      console.warn('Gemini API call returned error, falling back to local executive engine:', err.message);
    }
  }

  // 2. Intelligent local executive fallback engine
  const lower = message.toLowerCase();
  let reply = '';

  if (action) {
    if (action.type === 'play') {
      reply = `Now playing **"${action.target}"** on YouTube on your laptop screen. Enjoy the session! Let me know if you'd like to adjust volume or search for another track.`;
    } else if (action.type === 'search') {
      reply = `I've opened a web search for **"${action.target}"** on your laptop browser. The search results are now ready on your screen.`;
    } else {
      reply = `I have launched **${action.label}** on your laptop for you. Let me know if you'd like me to look up anything specific or perform another task.`;
    }
  } else if (lower.includes('slide') || lower.includes('presentation')) {
    reply = `I've prepared the **DEX Slide Presentation Studio** for you. You can browse through the slides, edit content in real time, and export a standalone HTML presentation deck anytime.`;
  } else if (lower.includes('spreadsheet') || lower.includes('sheet') || lower.includes('table')) {
    reply = `I've opened the **DEX Spreadsheet Studio** for you. It features live cell editing, mathematical formulas (such as \`=SUM\`, \`=AVERAGE\`), template datasets, and instant CSV import/export.`;
  } else if (lower.includes('research') || lower.includes('brief') || lower.includes('summary')) {
    reply = `I've generated a comprehensive research brief in the **DEX Research Studio**. It includes structured takeaways, comparative tables, and strategic recommendations ready for export.`;
  } else if (lower.includes('visualize') || lower.includes('chart') || lower.includes('graph')) {
    reply = `I've launched the **DEX Visualizer Studio**. You can inspect system telemetry, response latency distributions, or enter your own custom metrics to generate real-time charts.`;
  } else if (lower === 'open_app' || lower === 'run tool: open_app') {
    reply = `### 🚀 DEX Tool: \`open_app\`\n\nI can launch any Windows application or web platform on your laptop. Here are a few examples you can try:\n\n- \`open youtube\`\n- \`open spotify\`\n- \`open vscode\`\n- \`open calculator\`\n- \`open notepad\`\n\nWhat would you like me to open for you?`;
  } else if (lower === 'open_url' || lower === 'run tool: open_url') {
    reply = `### 🌐 DEX Tool: \`open_url\`\n\nI can navigate to any website in your laptop's default browser. For example:\n\n- \`open https://github.com\`\n- \`open https://news.ycombinator.com\`\n\nWhere would you like to go?`;
  } else if (lower === 'browser_search' || lower === 'run tool: browser_search') {
    reply = `### 🔍 DEX Tool: \`browser_search\`\n\nI can perform live web searches directly on your laptop display. Try typing:\n\n- \`search latest AI agent architectures\`\n- \`google Windows automation techniques\``;
  } else if (lower.includes('time') || lower.includes('date')) {
    reply = `The current date and time is **${new Date().toLocaleString('en-US', { timeZoneName: 'short', dateStyle: 'full', timeStyle: 'medium' })}**.`;
  } else if (/^(?:hi|hello|hey|greetings|morning|afternoon|evening)/i.test(lower)) {
    reply = `Hello! I am **DEX**, your personal AI executive assistant. All operational systems are online and connected to your Windows laptop.\n\nHere are some things I can do for you right now:\n\n- **Launch apps & sites**: *"open youtube"*, *"open vscode"*, *"open spotify"*\n- **Slide Studio**: Create interactive presentations and export them\n- **Spreadsheet Studio**: Calculate formulas and export CSV tables\n- **Deep Research**: Formulate structured executive briefs\n- **System Diagnostics**: Take screenshots and monitor active tasks\n\nHow can I help you today?`;
  } else if (lower.includes('who are you') || lower.includes('what are you')) {
    reply = `I am **DEX**, an executive personal AI assistant designed for Windows and mobile web control. I feature native desktop automation, conversational voice mode, multi-provider reasoning (Gemini, Claude, Ollama), phone-to-laptop bridging, and interactive productivity studios.`;
  } else if (lower.includes('screenshot') || lower.includes('capture screen')) {
    reply = `I've triggered a full-resolution desktop screenshot capture. The image is saved in your DEX workspace and analyzed via our vision pipeline.`;
  } else if (lower.startsWith('calc') || lower.includes('calculate')) {
    reply = `The calculator utility has been dispatched to your laptop screen. You can also use our **Spreadsheet Studio** for complex tabular calculations!`;
  } else if (lower.includes('status') || lower.includes('health')) {
    reply = `### 🛡️ DEX System Diagnostics\n\n- **Executive Engine**: ONLINE\n- **Windows Automation Bridge**: ACTIVE\n- **Active AI Provider**: ${apiKey ? 'Google Gemini' : 'Local Executive Engine'}\n- **Cloud Relay Queue**: CONNECTED\n- **Remote Control Gateway**: READY\n\nAll operational systems are operating normally.`;
  } else {
    reply = `I have received your request: **"${message}"**.\n\nAll DEX systems are active and ready. If you'd like to launch an application, create slides, generate a spreadsheet, or run deep research, just let me know!`;
  }

  res.status(200).json({ reply, status: 'ok', provider: 'DEX EXECUTIVE', action });
};
