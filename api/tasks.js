module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const tasks = [
    { id: 'task-101', description: 'Launch VS Code in workspace directory', status: 'COMPLETED', timestamp: '12 min ago' },
    { id: 'task-102', description: 'Screen vision OCR inspection', status: 'COMPLETED', timestamp: '28 min ago' },
    { id: 'task-103', description: 'Provider key rotation check', status: 'COMPLETED', timestamp: '1 hr ago' },
    { id: 'task-104', description: 'Android Companion PIN 849201 pairing', status: 'COMPLETED', timestamp: '3 hr ago' }
  ];

  res.status(200).json(tasks);
};

