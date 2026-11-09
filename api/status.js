module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  res.status(200).json({
    assistant: 'DEX',
    status: 'ONLINE',
    toolsCount: 24,
    userName: 'Muzain',
    setupCompleted: true,
    hasApiKey: true,
    activeProvider: 'GEMINI',
    version: '1.0.0'
  });
};

