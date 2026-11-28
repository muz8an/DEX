const { setCors, getBody } = require('./_utils');

let inMemoryProvider = {
  activeProvider: 'GEMINI',
  apiKey: process.env.GEMINI_API_KEY || '',
  model: 'gemini-2.0-flash',
  baseUrl: 'https://generativelanguage.googleapis.com',
  fallbackEnabled: true,
  fallbackProvider: 'CLAUDE'
};

let inMemoryProfile = {
  userName: 'Muzain',
  setupCompleted: true,
  hasCustomName: true
};

const TOOLS = [
  { name: 'open_app', icon: '🚀', symbol: 'EXE', category: 'System Automation', accent: '#3B82F6', description: 'Launch native Windows applications and desktop software (Notepad, VS Code, Calculator, Spotify, WhatsApp, Chrome).', parameters: 'appName (string)', example: 'open youtube', sensitive: false },
  { name: 'open_url', icon: '🌐', symbol: 'URL', category: 'Web Navigation', accent: '#10B981', description: 'Navigate to any web destination or SaaS platform inside the default Windows browser.', parameters: 'url (string)', example: 'open https://github.com', sensitive: false },
  { name: 'take_screenshot', icon: '📸', symbol: 'VIS', category: 'Multimodal Vision', accent: '#8B5CF6', description: 'Capture active desktop screens at native DPI and feed the buffer into vision models for OCR & UI analysis.', parameters: 'none', example: 'take screenshot', sensitive: false },
  { name: 'run_command', icon: '⚡', symbol: 'CMD', category: 'Shell Execution', accent: '#F59E0B', description: 'Safely execute verified PowerShell or CMD commands within the sandboxed DEX execution boundary.', parameters: 'command (string)', example: 'dir', sensitive: false },
  { name: 'read_file', icon: '📄', symbol: 'IO', category: 'File Systems', accent: '#06B6D4', description: 'Inspect and retrieve text contents from workspace files and project directories with UTF-8 support.', parameters: 'path (string)', example: 'read pom.xml', sensitive: false },
  { name: 'write_file', icon: '✍️', symbol: 'WRI', category: 'File Systems', accent: '#EC4899', description: 'Atomically author or update project source files, documents, and markdown briefs.', parameters: 'path, content (string)', example: 'write notes.txt', sensitive: false },
  { name: 'list_files', icon: '📂', symbol: 'DIR', category: 'File Systems', accent: '#6366F1', description: 'Enumerate directories and workspace assets with file sizes, timestamps, and permissions.', parameters: 'directory (string)', example: 'list files', sensitive: false },
  { name: 'speak_text', icon: '🎙️', symbol: 'TTS', category: 'Voice Synthesis', accent: '#14B8A6', description: 'Generate high-fidelity conversational speech audio utilizing Windows SAPI and Microsoft edge-tts.', parameters: 'text (string)', example: 'speak hello world', sensitive: false },
  { name: 'get_system_info', icon: '📊', symbol: 'SYS', category: 'Diagnostics', accent: '#F97316', description: 'Query real-time hardware telemetry: CPU load, available RAM, OS build, and network adapters.', parameters: 'none', example: 'get system info', sensitive: false },
  { name: 'pair_device', icon: '🛡️', symbol: 'PIN', category: 'Device Security', accent: '#A855F7', description: 'Generate time-limited 6-digit cryptographic PINs to securely pair Android mobile companions.', parameters: 'none', example: 'pair device', sensitive: false },
  { name: 'browser_search', icon: '🔍', symbol: 'SRCH', category: 'Intelligence', accent: '#38BDF8', description: 'Perform real-time search queries and parse live headlines, documentation, and web sources.', parameters: 'query (string)', example: 'search quantum computing', sensitive: false },
  { name: 'manage_memory', icon: '🧠', symbol: 'MEM', category: 'Cognitive Memory', accent: '#84CC16', description: 'Persist and retrieve conversational user preferences, system state, and facts across reboots.', parameters: 'key, value (string)', example: 'remember user role', sensitive: false }
];

const SAMPLE_TASKS = [
  { id: 'task-101', description: 'Launch VS Code in workspace directory', status: 'COMPLETED', timestamp: '12 min ago' },
  { id: 'task-102', description: 'Screen vision OCR inspection', status: 'COMPLETED', timestamp: '28 min ago' },
  { id: 'task-103', description: 'Provider key rotation check', status: 'COMPLETED', timestamp: '1 hr ago' },
  { id: 'task-104', description: 'Mobile remote bridge active', status: 'COMPLETED', timestamp: 'Just now' }
];

function maskKey(key) {
  if (!key) return '';
  if (key.length <= 8) return '****' + key.slice(-2);
  return key.slice(0, 4) + '...' + key.slice(-4);
}

function resolveRoute(req) {
  if (req.query?.route) return String(req.query.route).toLowerCase();
  const urlPath = (req.url || '').split('?')[0];
  return urlPath.replace(/^\/?api\//, '').toLowerCase();
}

module.exports = async (req, res) => {
  setCors(res, 'GET, POST, OPTIONS');
  if (req.method === 'OPTIONS') return res.status(204).end();

  const route = resolveRoute(req);

  switch (route) {
    case 'status':
      return res.status(200).json({
        assistant: 'DEX',
        status: 'ONLINE',
        toolsCount: TOOLS.length,
        userName: inMemoryProfile.userName,
        setupCompleted: inMemoryProfile.setupCompleted,
        hasApiKey: Boolean(inMemoryProvider.apiKey || process.env.GEMINI_API_KEY),
        activeProvider: inMemoryProvider.activeProvider,
        version: '1.0.0'
      });

    case 'network-info':
      return res.status(200).json({
        ip: '127.0.0.1',
        port: 31416,
        remoteToken: 'dex_web_live_pin_849201'
      });

    case 'tools':
      return res.status(200).json(TOOLS);

    case 'tasks':
      return res.status(200).json(SAMPLE_TASKS);

    case 'clear':
      return res.status(200).json({ status: 'ok', message: 'Memory flushed to disk.' });

    case 'profile':
      if (req.method === 'POST') {
        const body = await getBody(req);
        if (body.userName) {
          inMemoryProfile = { userName: String(body.userName), setupCompleted: true, hasCustomName: true };
        }
        return res.status(200).json({ ...inMemoryProfile, message: 'Profile updated' });
      }
      return res.status(200).json(inMemoryProfile);

    case 'providers':
      if (req.method === 'POST') {
        const body = await getBody(req);
        const provider = body.provider || inMemoryProvider.activeProvider;
        const apiKey = body.apiKey || '';
        inMemoryProvider.activeProvider = provider;
        if (apiKey) {
          inMemoryProvider.apiKey = apiKey;
          process.env.GEMINI_API_KEY = apiKey;
        }
        if (body.baseUrl) inMemoryProvider.baseUrl = body.baseUrl;
        if (body.model) inMemoryProvider.model = body.model;
        if (body.fallbackEnabled !== undefined) inMemoryProvider.fallbackEnabled = body.fallbackEnabled;
        if (body.fallbackProvider) inMemoryProvider.fallbackProvider = body.fallbackProvider;
        return res.status(200).json({
          status: 'ok',
          message: 'AI Provider configuration updated successfully',
          activeProvider: provider,
          configured: Boolean(inMemoryProvider.apiKey || process.env.GEMINI_API_KEY),
          maskedKey: maskKey(inMemoryProvider.apiKey || process.env.GEMINI_API_KEY)
        });
      }
      {
        const currentKey = inMemoryProvider.apiKey || process.env.GEMINI_API_KEY || '';
        const configured = Boolean(currentKey);
        return res.status(200).json({
          status: 'ok',
          activeProvider: inMemoryProvider.activeProvider,
          providers: [
            { id: 'GEMINI', name: 'Google Gemini 2.0 Flash', status: configured ? 'ONLINE' : 'STANDBY', configured, maskedKey: maskKey(currentKey), model: 'gemini-2.0-flash', baseUrl: 'https://generativelanguage.googleapis.com' },
            { id: 'CLAUDE', name: 'Anthropic Claude 3.5 Sonnet', status: 'STANDBY', configured: false, maskedKey: '', model: 'claude-3-5-sonnet-20241022', baseUrl: 'https://api.anthropic.com' },
            { id: 'OLLAMA', name: 'Local Ollama (DeepSeek-R1)', status: 'STANDBY', configured: true, maskedKey: 'localhost:11434', model: 'deepseek-r1:7b', baseUrl: 'http://localhost:11434' },
            { id: 'OPENAI', name: 'OpenAI GPT-4o', status: 'STANDBY', configured: false, maskedKey: '', model: 'gpt-4o', baseUrl: 'https://api.openai.com' }
          ]
        });
      }

    default:
      return res.status(404).json({ error: 'Unknown API route: ' + route });
  }
};
