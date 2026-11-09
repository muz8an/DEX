module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  res.status(200).json({
    activeProvider: 'GEMINI',
    providers: [
      { id: 'GEMINI', name: 'Google Gemini 2.5 Flash', status: 'ONLINE', isConfigured: true },
      { id: 'CLAUDE', name: 'Anthropic Claude 3.5 Sonnet', status: 'STANDBY', isConfigured: true },
      { id: 'OLLAMA', name: 'Local Ollama (DeepSeek-R1)', status: 'STANDBY', isConfigured: true },
      { id: 'OPENAI', name: 'OpenAI GPT-4o', status: 'STANDBY', isConfigured: false }
    ]
  });
};

