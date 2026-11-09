module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  const tools = [
    { name: 'open_app', description: 'Launch a Windows application executable (Notepad, VS Code, Calculator, Chrome)', category: 'System' },
    { name: 'open_url', description: 'Open a URL in the user default web browser', category: 'Browser' },
    { name: 'take_screenshot', description: 'Capture full desktop screen and analyze with OCR/multimodal vision', category: 'Vision' },
    { name: 'run_command', description: 'Execute a verified PowerShell or cmd command on Windows', category: 'Shell' },
    { name: 'read_file', description: 'Read file contents from local DEX workspace', category: 'Files' },
    { name: 'write_file', description: 'Create or update files in local DEX workspace', category: 'Files' },
    { name: 'list_files', description: 'List directories and files in workspace', category: 'Files' },
    { name: 'speak_text', description: 'Synthesize speech audio using Windows SAPI / edge-tts', category: 'Voice' },
    { name: 'get_system_info', description: 'Retrieve CPU, RAM, OS version, and network status', category: 'Diagnostics' },
    { name: 'pair_device', description: 'Generate 6-digit secure PIN for Android companion app', category: 'Security' },
    { name: 'browser_search', description: 'Perform web search query and extract results', category: 'Browser' },
    { name: 'manage_memory', description: 'Save and retrieve persistent conversational facts', category: 'Memory' }
  ];

  res.status(200).json(tools);
};

