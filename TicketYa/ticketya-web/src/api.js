import { load } from './store';

// Único punto de contacto con el backend: el API Gateway.
export const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export async function api(path, { method = 'GET', body, key } = {}) {
  const s = load('ty-session', {});
  const headers = {};
  if (s.role) { headers['X-User-Id'] = s.ids[s.role]; headers['X-User-Role'] = s.role; }
  if (body) headers['Content-Type'] = 'application/json';
  if (key) headers['Idempotency-Key'] = key;
  let r;
  try { r = await fetch(BASE + path, { method, headers, body: body && JSON.stringify(body) }); }
  catch { throw { detail: `No pudimos conectar con el API Gateway en ${BASE}. Verificá que esté en marcha y que esta app corra en el puerto 5173.` }; }
  const t = await r.text();
  let j = null; try { j = t ? JSON.parse(t) : null; } catch { /* cuerpo no JSON */ }
  if (!r.ok) throw { status: r.status, code: j?.code, detail: j?.detail || `Error ${r.status}` };
  return j;
}

export const eventsById = async (ids) => {
  const m = {};
  await Promise.all([...new Set(ids)].map((i) => api('/api/events/' + i).then((e) => { m[i] = e; }).catch(() => {})));
  return m;
};
