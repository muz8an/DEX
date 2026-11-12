// In-memory remote command queue for mobile-to-laptop bridging
let queue = [];
let nextId = 1;

function enqueueCommand(command, action, source = 'chat') {
  const target = action?.target || command;
  const item = {
    id: nextId++,
    command: command || ('open ' + target),
    action: action || { type: 'open', target, label: target },
    target: target,
    timestamp: Date.now(),
    source: source || 'mobile'
  };
  queue.push(item);
  if (queue.length > 50) queue.shift();
  return item;
}

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

const handler = async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    return res.status(204).end();
  }

  // Purge commands older than 60 seconds
  const now = Date.now();
  queue = queue.filter(item => (now - item.timestamp) < 60000);

  if (req.method === 'GET') {
    // Laptop poller requesting pending commands
    const pending = [...queue];
    const clear = req.query?.clear === '1' || req.query?.clear === 'true';
    if (clear) {
      queue = [];
    }
    return res.status(200).json({
      status: 'ok',
      count: pending.length,
      commands: pending
    });
  }

  if (req.method === 'POST') {
    const body = await getBody(req);

    // Check if this is an ACK (acknowledgement from laptop)
    if (body.ackId) {
      queue = queue.filter(item => item.id !== body.ackId);
      return res.status(200).json({ status: 'ok', acknowledged: body.ackId });
    }

    // New command from mobile client
    const command = (body.command || body.message || '').trim();
    const action = body.action || null;
    const target = body.target || action?.target || command;

    if (!command && !action && !target) {
      return res.status(400).json({ error: 'No command or action specified' });
    }

    const item = enqueueCommand(command, action, body.source || 'mobile');

    return res.status(200).json({
      status: 'ok',
      message: 'Command enqueued for laptop execution',
      item
    });
  }

  if (req.method === 'DELETE') {
    queue = [];
    return res.status(200).json({ status: 'ok', message: 'Queue cleared' });
  }

  res.status(405).json({ error: 'Method not allowed' });
};

handler.enqueueCommand = enqueueCommand;
module.exports = handler;
