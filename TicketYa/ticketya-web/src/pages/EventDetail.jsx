import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Async, Back, Badge, H1, TYPES, money, useAsync, useUI, when } from '../ui';

const LIM = 'El límite es de 6 entradas por evento.';

function SeatGrid({ s, sel = [], onToggle }) {
  const rows = {};
  (s.seats || []).forEach((x) => (rows[x.rowLabel] ??= []).push(x));
  return (
    <>
      <p className="mut">Gris rayado: no disponible.</p>
      {Object.entries(rows).map(([r, a]) => (
        <div className="sr" role="group" aria-label={`Fila ${r}`} key={r}>
          <span className="rl">{r}</span>
          {a.sort((x, y) => x.number - y.number).map((x) => {
            const ok = x.status === 'AVAILABLE';
            return <button key={x.seatId} type="button" className="seat" disabled={!ok} aria-pressed={sel.includes(x.seatId)}
              aria-label={`Fila ${r}, asiento ${x.number}${ok ? '' : ', no disponible'}`} onClick={() => onToggle(x.seatId)}>{x.number}</button>;
          })}
        </div>
      ))}
    </>
  );
}

function Detail({ e, av }) {
  const { role } = useAuth();
  const nav = useNavigate();
  const { notify, run } = useUI();
  const [seats, setSeats] = useState({});
  const [qty, setQty] = useState({});
  const [busy, setBusy] = useState(false);
  const key = useRef(null); // Idempotency-Key estable mientras la selección no cambie
  const price = Object.fromEntries((e.prices || []).map((p) => [p.sectorId, p.price]));
  const n = Object.values(seats).flat().length + Object.values(qty).reduce((a, b) => a + b, 0);
  const total = Object.entries(seats).reduce((a, [k, v]) => a + v.length * price[k], 0) + Object.entries(qty).reduce((a, [k, v]) => a + v * price[k], 0);
  const buyable = e.status === 'PUBLISHED' && av;

  const toggle = (sid, id) => {
    const cur = seats[sid] || [];
    if (!cur.includes(id) && n >= 6) return notify(LIM, true);
    key.current = null;
    setSeats({ ...seats, [sid]: cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id] });
  };
  const setQ = (sid, v, max) => {
    const others = n - (qty[sid] || 0);
    v = Math.max(0, Math.min(max, Math.floor(v) || 0));
    if (others + v > 6) { v = 6 - others; notify(LIM, true); }
    key.current = null; setQty({ ...qty, [sid]: v });
  };
  const buy = () => run(async () => {
    if (!role) return nav('/login');
    if (role !== 'CUSTOMER') return notify('Solo los clientes pueden comprar entradas.', true);
    const items = [
      ...Object.entries(seats).filter(([, v]) => v.length).map(([sectorId, seatIds]) => ({ sectorId, seatIds })),
      ...Object.entries(qty).filter(([, v]) => v > 0).map(([sectorId, quantity]) => ({ sectorId, quantity })),
    ];
    setBusy(true);
    try { const o = await api('/api/orders', { method: 'POST', body: { eventId: e.id, items }, key: (key.current ??= crypto.randomUUID()) }); nav('/checkout/' + o.orderId); }
    finally { setBusy(false); }
  });

  return (
    <>
      <Back to="/events">eventos</Back>
      <div className="detail">
        <div>
          <H1>{e.name}</H1>
          <p><Badge s={e.status} /> <span className="chip">{TYPES[e.type] || e.type}</span></p>
          <p><b>{when(e.startsAt)}</b></p><p>{e.city}</p><p>{e.description}</p>
          {!!e.participants?.length && <><h2>Participan</h2><ul>{e.participants.map((p) => <li key={p.name}>{p.name}</li>)}</ul></>}
        </div>
        <div>
          <h2>Elegí tus entradas</h2>
          {(e.prices || []).map((p) => {
            const s = av?.sectors.find((x) => x.sectorId === p.sectorId);
            return (
              <section className="panel" key={p.sectorId} aria-labelledby={`s${p.sectorId}`}>
                <div className="sh"><h3 id={`s${p.sectorId}`}>{p.sectorName}</h3><b>{money(p.price, p.currency)}</b></div>
                {s && <p className="mut">{s.available > 0 ? `Quedan ${s.available} de ${s.capacity}` : 'Agotado'}</p>}
                {buyable && s?.available > 0 && (s.type === 'NUMBERED'
                  ? <SeatGrid s={s} sel={seats[s.sectorId]} onToggle={(id) => toggle(s.sectorId, id)} />
                  : <label>Cantidad de entradas<input type="number" min="0" max={Math.min(6, s.available)} value={qty[s.sectorId] || 0} onChange={(ev) => setQ(s.sectorId, +ev.target.value, Math.min(6, s.available))} /></label>)}
              </section>
            );
          })}
          {!(e.prices || []).length && <p className="mut">Este evento todavía no tiene precios.</p>}
          {buyable
            ? <div className="sum" aria-live="polite"><span>{n ? <><b>{n} {n > 1 ? 'entradas' : 'entrada'}</b>, total <b>{money(total)}</b></> : 'Elegí tus lugares para continuar'}</span>
                <button className="btn" disabled={busy || (!n && !!role)} onClick={buy}>{role ? 'Reservar entradas' : 'Ingresar para comprar'}</button></div>
            : <p className="alert">{e.status === 'SOLD_OUT' ? 'Las entradas están agotadas.' : 'Este evento no está a la venta por el momento.'}</p>}
        </div>
      </div>
    </>
  );
}

export default function EventDetail() {
  const { id } = useParams();
  const s = useAsync(() => Promise.all([api('/api/events/' + id), api(`/api/events/${id}/availability`).catch(() => null)]), [id]);
  return <Async s={s}>{([e, av]) => <Detail e={e} av={av} />}</Async>;
}
