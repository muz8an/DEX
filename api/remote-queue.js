// In-memory remote command queue + live connected device telemetry for mobile-to-laptop bridging
let queue = [];
let history = [];
const devices = new Map();
let nextId = 1;

function pruneState(now) {
  queue = queue.filter(item => (now - item.timestamp) < 75000);
  for (const [id, dev] of devices.entries()) {
    if ((now - dev.lastSeen) > 300000) {
      devices.delete(id);
    }
  }
}

function snapshotDevices(now) {
  const list = [];
  let host = null;
  for (const dev of devices.values()) {
    const entry = {
      ...dev,
      online: (now - dev.lastSeen) < 60000
    };
    list.push(entry);
    if (dev.role === 'desktop') {
      host = entry;
    }
  }
  list.sort((a, b) => b.lastSeen - a.lastSeen);
  return { list, host };
}

function upsertDevice(meta, now, didExecuteCommand) {
  if (!meta) return null;
  const role = meta.role === 'desktop' ? 'desktop' : 'mobile';
  const rawId = meta.deviceId || (role === 'desktop' ? 'laptop-host' : '');
  if (!rawId) return null;

  const existing = devices.get(rawId) || {
    deviceId: rawId,
    role,
    name: role === 'desktop' ? 'Windows Laptop' : 'Mobile Device',
    osBrowser: role === 'desktop' ? 'Windows / DEX' : 'Mobile Web',
    screen: '',
    orientation: '',
    fullscreen: false,
    port: meta.port || 31416,
    lanIp: meta.lanIp || '',
    connectedAt: now,
    lastSeen: now,
    commandsSent: 0,
    lastCommand: ''
  };

  if (meta.name || meta.deviceName) existing.name = String(meta.name || meta.deviceName).slice(0, 80);
  if (meta.osBrowser || meta.os) existing.osBrowser = String(meta.osBrowser || meta.os).slice(0, 80);
  if (typeof meta.screen === 'string' && meta.screen) existing.screen = meta.screen.slice(0, 40);
  if (typeof meta.orientation === 'string' && meta.orientation) existing.orientation = meta.orientation.slice(0, 24);
  if (typeof meta.fullscreen === 'boolean') existing.fullscreen = meta.fullscreen;
  if (meta.port) existing.port = meta.port;
  if (meta.lanIp) existing.lanIp = meta.lanIp;
  existing.role = role;
  existing.lastSeen = now;
  if (didExecuteCommand) {
    existing.commandsSent = (existing.commandsSent || 0) + 1;
    existing.lastCommand = String(didExecuteCommand).slice(0, 120);
  }
  devices.set(rawId, existing);
  return existing;
}

function enqueueCommand(command, action, source = 'chat', deviceMeta = null) {
  const now = Date.now();
  const target = action?.target || command;
  const item = {
    id: nextId++,
    command: command || ('open ' + target),
    action: action || { type: 'open', target, label: target },
    target,
    timestamp: now,
    source: source || 'mobile',
    deviceId: deviceMeta?.deviceId || '',
    deviceName: deviceMeta?.deviceName || deviceMeta?.name || '',
    osBrowser: deviceMeta?.osBrowser || '',
    screen: deviceMeta?.screen || '',
    orientation: deviceMeta?.orientation || '',
    fullscreen: Boolean(deviceMeta?.fullscreen),
    browserNotified: false
  };
  queue.push(item);
  if (queue.length > 50) queue.shift();
  if (deviceMeta?.deviceId) {
    upsertDevice({ ...deviceMeta, role: 'mobile' }, now, item.command);
  }
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

  const now = Date.now();
  pruneState(now);

  if (req.method === 'GET') {
    const consumer = (req.query?.consumer || 'desktop').toLowerCase();
    const pending = consumer === 'browser'
      ? queue.filter(item => !item.browserNotified)
      : [...queue];

    if (req.query?.clear === '1' || req.query?.clear === 'true') {
      queue = [];
    }

    const { list, host } = snapshotDevices(now);
    const mobileDevices = list.filter(d => d.role === 'mobile');
    return res.status(200).json({
      status: 'ok',
      count: pending.length,
      commands: pending,
      devices: list,
      mobileDevices,
      onlineMobileCount: mobileDevices.filter(d => d.online).length,
      host,
      hostOnline: Boolean(host && host.online),
      history: history.slice(0, 12)
    });
  }

  if (req.method === 'POST') {
    const body = await getBody(req);

    // 1. Device / Host Heartbeat
    if (body.type === 'heartbeat') {
      const updated = upsertDevice(body, now, null);
      const { list, host } = snapshotDevices(now);
      const mobileDevices = list.filter(d => d.role === 'mobile');
      return res.status(200).json({
        status: 'ok',
        device: updated,
        devices: list,
        mobileDevices,
        onlineMobileCount: mobileDevices.filter(d => d.online).length,
        host,
        hostOnline: Boolean(host && host.online),
        history: history.slice(0, 12)
      });
    }

    // 2. Acknowledgement (distinguish Desktop Java Bridge vs Browser UI)
    if (body.ackId) {
      const consumer = (body.consumer || 'desktop').toLowerCase();
      if (consumer === 'browser') {
        const found = queue.find(item => item.id === body.ackId);
        if (found) found.browserNotified = true;
        return res.status(200).json({ status: 'ok', browserNotified: body.ackId });
      }
      const removed = queue.find(item => item.id === body.ackId);
      queue = queue.filter(item => item.id !== body.ackId);
      if (removed) {
        history.unshift({
          id: removed.id,
          command: removed.command,
          target: removed.target,
          deviceName: removed.deviceName || 'Mobile Remote',
          executedAt: now,
          result: body.result || 'Executed on Windows Laptop'
        });
        if (history.length > 20) history.pop();
      }
      return res.status(200).json({ status: 'ok', acknowledged: body.ackId });
    }

    // 3. New command from mobile client
    const command = (body.command || body.message || '').trim();
    const action = body.action || null;
    const target = body.target || action?.target || command;

    if (!command && !action && !target) {
      return res.status(400).json({ error: 'No command or action specified' });
    }

    const item = enqueueCommand(command, action, body.source || 'mobile', {
      deviceId: body.deviceId,
      deviceName: body.deviceName || body.name,
      osBrowser: body.osBrowser,
      screen: body.screen,
      orientation: body.orientation,
      fullscreen: body.fullscreen
    });

    const { list, host } = snapshotDevices(now);
    return res.status(200).json({
      status: 'ok',
      message: 'Command enqueued for laptop execution',
      item,
      hostOnline: Boolean(host && host.online),
      onlineMobileCount: list.filter(d => d.role === 'mobile' && d.online).length
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
