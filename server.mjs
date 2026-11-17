import http from 'node:http';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Transit } from './lib/transit.mjs';
import { DEFAULT_SCHEDULE, validateSchedule } from './lib/schedule.mjs';
const root = path.dirname(fileURLToPath(import.meta.url)), settingsPath = process.env.CONFIG_PATH || path.join(root, '.local/settings.json');
let config = { ...DEFAULT_SCHEDULE, mode: 'demo', busKey: process.env.BUS_API_KEY || '', subwayKey: process.env.SUBWAY_API_KEY || '', enabled: false };
try { config = { ...config, ...JSON.parse(await readFile(settingsPath, 'utf8')) }; } catch {}
const transit = new Transit(); let pending;
function publicConfig() { return { ...Object.fromEntries(Object.keys(DEFAULT_SCHEDULE).map(k => [k,config[k]])), mode: config.mode, enabled: config.enabled, proximityEnabled: !!config.proximityEnabled, busKeySet: !!config.busKey, subwayKeySet: !!config.subwayKey, platform: 'pc', notificationsGranted: false, exactAlarmGranted: false }; }
const server = http.createServer(async (req, res) => {
  res.setHeader('Cache-Control', 'no-store'); res.setHeader('X-Content-Type-Options', 'nosniff');
  if (!/^(127\.0\.0\.1|localhost)(:\d+)?$/.test(req.headers.host || '')) { res.writeHead(403).end(); return; }
  const url = new URL(req.url, 'http://127.0.0.1');
  try {
    if (req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) { res.writeHead(403).end(); return; }
    if (url.pathname.startsWith('/api/')) {
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      if (url.pathname === '/api/config' && req.method === 'GET') return res.end(JSON.stringify(publicConfig()));
      if (url.pathname === '/api/config' && req.method === 'POST') {
        if (!req.headers['content-type']?.startsWith('application/json')) { res.writeHead(415).end('{}'); return; }
        let body = ''; for await (const chunk of req) { body += chunk; if (body.length > 16000) throw new Error('설정이 너무 큽니다.'); }
        const input = JSON.parse(body);
        const next = { ...config };
        for (const key of Object.keys(DEFAULT_SCHEDULE)) if (typeof input[key] === 'string') next[key] = input[key];
        try { validateSchedule(next); } catch (e) { res.writeHead(400).end(JSON.stringify({ error: e.message })); return; }
        if (input.mode === 'demo' || input.mode === 'live') next.mode = input.mode;
        if (typeof input.enabled === 'boolean') next.enabled = input.enabled;
        if (typeof input.proximityEnabled === 'boolean') next.proximityEnabled = input.proximityEnabled;
        for (const key of ['busKey', 'subwayKey']) if (typeof input[key] === 'string' && input[key].trim()) next[key] = input[key].trim();
        if (input.clearKeys) { next.busKey = ''; next.subwayKey = ''; }
        if(pending) await pending.catch(()=>{});
        await mkdir(path.dirname(settingsPath), { recursive: true }); await writeFile(settingsPath, JSON.stringify(next), { mode: 0o600 });
        config = next; transit.reset(); return res.end(JSON.stringify(publicConfig()));
      }
      if (url.pathname === '/api/snapshot' && req.method === 'GET') {
        // Serialise overlapping clients; do not multiply upstream API use.
        if (!pending) pending = transit.snapshot(config, url.searchParams.get('test') === '1').finally(() => { pending = null; });
        return res.end(JSON.stringify(await pending));
      }
      res.writeHead(404).end('{}'); return;
    }
    if (req.method !== 'GET') { res.writeHead(405).end(); return; }
    const allowed = { '/': ['index.html', 'text/html'], '/app.js': ['app.js', 'text/javascript'], '/style.css': ['style.css', 'text/css'], '/icon.svg': ['icon.svg', 'image/svg+xml'] };
    const asset = allowed[url.pathname]; if (!asset) { res.writeHead(404).end(); return; }
    res.setHeader('Content-Type', `${asset[1]}; charset=utf-8`); res.end(await readFile(path.join(root, 'web', asset[0])));
  } catch { res.writeHead(502).end(JSON.stringify({ error: '요청 처리 실패. 설정 또는 네트워크를 확인하세요.' })); }
});
server.listen(Number(process.env.PORT || 4173), '127.0.0.1', () => console.log(`퇴근길 버스 PC 테스트: http://127.0.0.1:${server.address().port}`));
