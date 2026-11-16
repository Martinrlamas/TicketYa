import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

export const money = (n, c = 'ARS') => new Intl.NumberFormat('es-AR', { style: 'currency', currency: c, maximumFractionDigits: 0 }).format(n);
export const when = (d) => new Date(d).toLocaleString('es-AR', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' });
export const day = (d) => new Date(d).toLocaleDateString('es-AR');
export const short = (i) => String(i || '').slice(0, 8);
export const TYPES = { CONCERT: 'Concierto', THEATER: 'Teatro', SPORT: 'Deporte', CONFERENCE: 'Conferencia', FESTIVAL: 'Festival', OTHER: 'Otro' };
export const ST = Object.fromEntries('DRAFT:Borrador,PUBLISHED:A la venta,SOLD_OUT:Agotado,SUSPENDED:Suspendido,CANCELLED:Cancelado,FINISHED:Finalizado,PENDING_PAYMENT:Pago pendiente,CONFIRMED:Confirmada,PAYMENT_REJECTED:Pago rechazado,EXPIRED:Vencida,FAILED_REFUNDED:Reintegrada,CANCELLED_BY_EVENT:Evento cancelado,VALID:Válida,USED:Usada,REFUNDED:Reintegrada,PENDING:Pendiente,REJECTED:Rechazada,APPROVED:Aprobada,PROCESSING:Procesando,COMPLETED:Completada,FAILED:Con error,ACTIVE:Activa,REFUND_REQUESTED:Devolución pedida'.split(',').map((x) => x.split(':')));
const tone = (s) => /^(PUBLISHED|CONFIRMED|VALID|COMPLETED|APPROVED|ACTIVE)$/.test(s) ? 'ok' : /^(CANCELLED|REJECTED|FAILED|PAYMENT_REJECTED|SUSPENDED|EXPIRED|REFUNDED|SOLD_OUT)/.test(s) ? 'bad' : 'warn';

// Cada evento tiene su propio color (estable según su id): lo comparten la tarjeta del catálogo y la entrada.
const PAL = [
  ['#2F5BFF', '#1B3BC0', '#DCE4FF'], ['#D9430F', '#A8310A', '#FFE3D6'], ['#0E7A5A', '#0A5A42', '#D4F0E6'], ['#B0246F', '#801A52', '#F9DCEC'],
  ['#5B3FD9', '#3F28A8', '#E6DFFF'], ['#0A6C8A', '#064C61', '#D3EEF6'], ['#C2272D', '#8E1B20', '#FBDCDD'],
];
const hash = (s) => { let h = 2166136261; for (const c of String(s)) { h ^= c.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; };
export const hue = (id) => { const [c, d, p] = PAL[hash(id) % PAL.length]; return { '--c': c, '--cd': d, '--cp': p }; };
// Código de barras decorativo, determinista por semilla (el QR escaneable de la entrada es aparte).
export const Barcode = ({ seed, n = 30 }) => {
  let h = hash(seed) || 1, x = 0; const bars = [];
  for (let i = 0; i < n; i++) { h = Math.imul(h ^ (h >>> 15), 2246822519) >>> 0; const w = 1 + (h % 3), g = 1 + ((h >> 4) % 2); bars.push(<rect key={i} x={x} y="0" width={w} height="40" />); x += w + g; }
  return <svg className="bars" viewBox={`0 0 ${x} 40`} preserveAspectRatio="none" aria-hidden="true" fill="currentColor">{bars}</svg>;
};

export const Badge = ({ s }) => <span className={`badge ${tone(s)}`}>{ST[s] || s}</span>;
export const H1 = ({ children, sub }) => <><h1 tabIndex={-1}>{children}</h1>{sub && <p className="sub">{sub}</p>}</>;
export const Back = ({ to, children }) => <Link className="back" to={to}>Volver a {children}</Link>;
export const Empty = ({ title, text, to, cta }) => (
  <div className="empty"><h2>{title}</h2><p>{text}</p>{to && <Link className="btn" to={to}>{cta}</Link>}</div>
);

// Carga asincrónica con estados de carga/error y recarga manual.
export function useAsync(fn, deps) {
  const [s, set] = useState({});
  const [n, setN] = useState(0);
  useEffect(() => {
    let live = true;
    fn().then((data) => live && set({ data })).catch((error) => live && set((x) => ({ ...x, error })));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n]);
  return { ...s, reload: () => setN((x) => x + 1) };
}
export function Async({ s, children }) {
  if (s.error) return <div className="empty" role="alert"><h2>No pudimos cargar esta pantalla</h2><p>{s.error.detail}</p><button className="btn" onClick={s.reload}>Reintentar</button></div>;
  if (!s.data) return <p role="status" className="sub">Cargando…</p>;
  return children(s.data);
}

const Ctx = createContext();
export const useUI = () => useContext(Ctx);

// Notificaciones (región aria-live) y diálogos de confirmación accesibles.
export function UIProvider({ children }) {
  const [toast, setToast] = useState(null);
  const [d, setD] = useState(null);
  const timer = useRef(); const dlg = useRef();
  const notify = useCallback((msg, bad) => { setToast({ msg, bad }); clearTimeout(timer.current); timer.current = setTimeout(() => setToast(null), bad ? 8000 : 4000); }, []);
  const ask = useCallback((title, opts = {}) => new Promise((res) => setD({ title, opts, res })), []);
  const run = useCallback((fn) => fn().catch((e) => notify(e.detail || 'Ocurrió un error inesperado.', true)), [notify]);
  useEffect(() => { if (d && dlg.current && !dlg.current.open) dlg.current.showModal(); }, [d]);
  const close = (v) => { dlg.current.close(); d.res(v); setD(null); };
  return (
    <Ctx.Provider value={{ notify, ask, run }}>
      {children}
      <div id="toast" role="status" aria-live="polite" className={toast ? (toast.bad ? 'bad show' : 'show') : ''}>{toast?.msg}</div>
      <dialog ref={dlg} aria-labelledby="dt" onCancel={(e) => { e.preventDefault(); close(null); }}>
        {d && (
          <form onSubmit={(e) => { e.preventDefault(); close(d.opts.label ? new FormData(e.target).get('r') : true); }}>
            <h2 id="dt">{d.title}</h2>
            {d.opts.label && <label>{d.opts.label}<textarea name="r" rows="3" required={d.opts.req} /></label>}
            <div className="row"><button type="button" className="btn ghost" onClick={() => close(null)}>Volver</button><button className="btn">{d.opts.ok || 'Confirmar'}</button></div>
          </form>
        )}
      </dialog>
    </Ctx.Provider>
  );
}
