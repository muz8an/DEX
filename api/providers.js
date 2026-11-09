let inMemoryProvider = {
  activeProvider: 'GEMINI',
  apiKey: process.env.GEMINI_API_KEY || '',
  model: 'gemini-2.0-flash',
  baseUrl: 'https://generativelanguage.googleapis.com',
  fallbackEnabled: true,
  fallbackProvider: 'CLAUDE'
};

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

function maskKey(key) {
  if (!key) return '';
  if (key.length <= 8) return '****' + key.slice(-2);
  return key.slice(0, 4) + '...' + key.slice(-4);
}

module.exports = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  if (req.method === 'POST') {
    const body = await getBody(req);
    const provider = body.provider || inMemoryProvider.activeProvider;
    const apiKey = body.apiKey || '';
    const baseUrl = body.baseUrl || inMemoryProvider.baseUrl;
    const model = body.model || inMemoryProvider.model;
    const fallbackEnabled = body.fallbackEnabled !== undefined ? body.fallbackEnabled : inMemoryProvider.fallbackEnabled;
    const fallbackProvider = body.fallbackProvider || inMemoryProvider.fallbackProvider;

    inMemoryProvider.activeProvider = provider;
    if (apiKey) {
      inMemoryProvider.apiKey = apiKey;
      process.env.GEMINI_API_KEY = apiKey;
    }
    inMemoryProvider.baseUrl = baseUrl;
    inMemoryProvider.model = model;
    inMemoryProvider.fallbackEnabled = fallbackEnabled;
    inMemoryProvider.fallbackProvider = fallbackProvider;

    return res.status(200).json({
      status: 'ok',
      message: 'AI Provider configuration updated successfully',
      activeProvider: provider,
      configured: Boolean(inMemoryProvider.apiKey || process.env.GEMINI_API_KEY),
      maskedKey: maskKey(inMemoryProvider.apiKey)
    });
  }

  // GET handler
  const isConfigured = Boolean(inMemoryProvider.apiKey || process.env.GEMINI_API_KEY);
  const currentKey = inMemoryProvider.apiKey || process.env.GEMINI_API_KEY || '';

  res.status(200).json({
    status: 'ok',
    activeProvider: inMemoryProvider.activeProvider,
    providers: [
      {
        id: 'GEMINI',
        name: 'Google Gemini 2.0 Flash',
        status: isConfigured ? 'ONLINE' : 'STANDBY',
        configured: isConfigured,
        maskedKey: maskKey(currentKey),
        model: 'gemini-2.0-flash',
        baseUrl: 'https://generativelanguage.googleapis.com'
      },
      {
        id: 'CLAUDE',
        name: 'Anthropic Claude 3.5 Sonnet',
        status: 'STANDBY',
        configured: false,
        maskedKey: '',
        model: 'claude-3-5-sonnet-20241022',
        baseUrl: 'https://api.anthropic.com'
      },
      {
        id: 'OLLAMA',
        name: 'Local Ollama (DeepSeek-R1)',
        status: 'STANDBY',
        configured: true,
        maskedKey: 'localhost:11434',
        model: 'deepseek-r1:7b',
        baseUrl: 'http://localhost:11434'
      },
      {
        id: 'OPENAI',
        name: 'OpenAI GPT-4o',
        status: 'STANDBY',
        configured: false,
        maskedKey: '',
        model: 'gpt-4o',
        baseUrl: 'https://api.openai.com'
      }
    ]
  });
};
