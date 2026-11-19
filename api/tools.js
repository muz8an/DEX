module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const tools = [
    {
      name: 'open_app',
      icon: '🚀',
      symbol: 'EXE',
      category: 'System Automation',
      accent: '#3B82F6',
      description: 'Launch native Windows applications and desktop software (Notepad, VS Code, Calculator, Spotify, WhatsApp, Chrome).',
      parameters: 'appName (string)',
      example: 'open youtube',
      sensitive: false
    },
    {
      name: 'open_url',
      icon: '🌐',
      symbol: 'URL',
      category: 'Web Navigation',
      accent: '#10B981',
      description: 'Navigate to any web destination or SaaS platform inside the default Windows browser.',
      parameters: 'url (string)',
      example: 'open https://github.com',
      sensitive: false
    },
    {
      name: 'take_screenshot',
      icon: '📸',
      symbol: 'VIS',
      category: 'Multimodal Vision',
      accent: '#8B5CF6',
      description: 'Capture active desktop screens at native DPI and feed the buffer into vision models for OCR & UI analysis.',
      parameters: 'none',
      example: 'take screenshot',
      sensitive: false
    },
    {
      name: 'run_command',
      icon: '⚡',
      symbol: 'CMD',
      category: 'Shell Execution',
      accent: '#F59E0B',
      description: 'Safely execute verified PowerShell or CMD commands within the sandboxed DEX execution boundary.',
      parameters: 'command (string)',
      example: 'dir',
      sensitive: false
    },
    {
      name: 'read_file',
      icon: '📄',
      symbol: 'IO',
      category: 'File Systems',
      accent: '#06B6D4',
      description: 'Inspect and retrieve text contents from workspace files and project directories with UTF-8 support.',
      parameters: 'path (string)',
      example: 'read pom.xml',
      sensitive: false
    },
    {
      name: 'write_file',
      icon: '✍️',
      symbol: 'WRI',
      category: 'File Systems',
      accent: '#EC4899',
      description: 'Atomically author or update project source files, documents, and markdown briefs.',
      parameters: 'path, content (string)',
      example: 'write notes.txt',
      sensitive: false
    },
    {
      name: 'list_files',
      icon: '📂',
      symbol: 'DIR',
      category: 'File Systems',
      accent: '#6366F1',
      description: 'Enumerate directories and workspace assets with file sizes, timestamps, and permissions.',
      parameters: 'directory (string)',
      example: 'list files',
      sensitive: false
    },
    {
      name: 'speak_text',
      icon: '🎙️',
      symbol: 'TTS',
      category: 'Voice Synthesis',
      accent: '#14B8A6',
      description: 'Generate high-fidelity conversational speech audio utilizing Windows SAPI and Microsoft edge-tts.',
      parameters: 'text (string)',
      example: 'speak hello world',
      sensitive: false
    },
    {
      name: 'get_system_info',
      icon: '📊',
      symbol: 'SYS',
      category: 'Diagnostics',
      accent: '#F97316',
      description: 'Query real-time hardware telemetry: CPU load, available RAM, OS build, and network adapters.',
      parameters: 'none',
      example: 'get system info',
      sensitive: false
    },
    {
      name: 'pair_device',
      icon: '🛡️',
      symbol: 'PIN',
      category: 'Device Security',
      accent: '#A855F7',
      description: 'Generate time-limited 6-digit cryptographic PINs to securely pair Android mobile companions.',
      parameters: 'none',
      example: 'pair device',
      sensitive: false
    },
    {
      name: 'browser_search',
      icon: '🔍',
      symbol: 'SRCH',
      category: 'Intelligence',
      accent: '#38BDF8',
      description: 'Perform real-time search queries and parse live headlines, documentation, and web sources.',
      parameters: 'query (string)',
      example: 'search quantum computing',
      sensitive: false
    },
    {
      name: 'manage_memory',
      icon: '🧠',
      symbol: 'MEM',
      category: 'Cognitive Memory',
      accent: '#84CC16',
      description: 'Persist and retrieve conversational user preferences, system state, and facts across reboots.',
      parameters: 'key, value (string)',
      example: 'remember user role',
      sensitive: false
    }
  ];

  res.status(200).json(tools);
};

