import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { load, save } from '../store';
import { Async, Back, Badge, H1, ST, money, useAsync, useUI, when } from '../ui';
import { Ticket } from './Tickets';

const Summary = ({ o, ev }) => (
  <section className="panel">
    <h2>{ev.name}</h2><p>{when(o.eventStartsAt)}</p>
    <ul className="lines">{o.items.map((i) => <li key={i.itemId}><span>{i.seatLabel || 'Entrada general'}</span><span>{money(i.unitPrice, i.currency)}</span></li>)}</ul>
    <p className="tot">Total <b>{money(o.total, o.currency)}</b></p>
  </section>
);

function Pending({ o, ev, reload }) {
  const { notify, ask, run } = useUI();
  const nav = useNavigate();
  const exp = new Date(o.holdExpiresAt).getTime();
  const left = () => Math.max(0, Math.round((exp - Date.now()) / 1000));
  const [sec, setSec] = useState(left);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const t = setInterval(() => { const s = left(); setSec(s); if (!s) { clearInterval(t); notify('La reserva venció. Volvé a elegir tus lugares.', true); reload(); } }, 1000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exp]);

  const pay = (e) => { e.preventDefault(); const f = new FormData(e.target); return run(async () => {
    const keys = load('ty-pay', {}); const key = (keys[o.orderId] ??= crypto.randomUUID()); save('ty-pay', keys); // misma clave en reintentos
    setBusy(true);
    try { await api(`/api/orders/${o.orderId}/pay`, { method: 'POST', body: { method: 'CARD', token: f.get('token'), last4: f.get('last4') }, key }); notify('Pago aprobado. Ya tenés tus entradas.'); }
    catch (x) { notify(x.detail, true); }
    setBusy(false); reload();
  }); };
  const cancel = () => run(async () => {
    if (!(await ask('¿Cancelar la reserva?', { ok: 'Cancelar reserva' }))) return;
    await api('/api/orders/' + o.orderId, { method: 'DELETE' }); notify('Reserva cancelada.'); nav('/orders');
  });
  return (
    <>
      <Back to="/orders">mis compras</Back><H1>Pagá tu reserva</H1>
      <p className="alert">Tus lugares están reservados por <b role="timer">{String(sec / 60 | 0).padStart(2, '0')}:{String(sec % 60).padStart(2, '0')}</b>. Si no pagás a tiempo, se liberan.</p>
      <Summary o={o} ev={ev} />
      <form className="panel" onSubmit={pay}>
        <h2>Pago con tarjeta</h2><p className="mut">Entorno de pruebas: elegí cómo responde la pasarela simulada.</p>
        <label>Resultado simulado<select name="token"><option value="tok_approved">Aprobar el pago</option><option value="tok_rejected">Rechazar el pago</option><option value="tok_timeout">Simular demora de la pasarela</option><option value="tok_flaky">Simular falla intermitente</option></select></label>
        <label>Últimos 4 dígitos de la tarjeta<input name="last4" inputMode="numeric" pattern="\d{4}" maxLength={4} defaultValue="4242" required autoComplete="off" /></label>
        <div className="actions"><button className="btn" disabled={busy}>Pagar {money(o.total, o.currency)}</button><button type="button" className="btn ghost" onClick={cancel}>Cancelar reserva</button></div>
      </form>
    </>
  );
}

export default function Checkout() {
  const { id } = useParams();
  const s = useAsync(async () => { const o = await api('/api/orders/' + id); return { o, ev: await api('/api/events/' + o.eventId).catch(() => ({ name: 'Evento' })) }; }, [id]);
  return (
    <Async s={s}>{({ o, ev }) => o.status === 'PENDING_PAYMENT' ? <Pending o={o} ev={ev} reload={s.reload} /> : (
      <>
        <Back to="/orders">mis compras</Back>
        <H1>{o.status === 'CONFIRMED' ? 'Compra confirmada' : `Orden ${(ST[o.status] || '').toLowerCase()}`}</H1>
        <p><Badge s={o.status} /></p><Summary o={o} ev={ev} />
        {o.status === 'CONFIRMED'
          ? o.tickets ? <><h2>Tus entradas</h2><ul className="tks">{o.tickets.map((t) => <Ticket key={t.ticketId} t={t} ev={ev} />)}</ul></>
            : <p className="alert">Estamos emitiendo tus entradas. <button className="btn ghost sm" onClick={s.reload}>Actualizar</button></p>
          : <Link className="btn" to="/events">Buscar otros eventos</Link>}
      </>
    )}</Async>
  );
}
