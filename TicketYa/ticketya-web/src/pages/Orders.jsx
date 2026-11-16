import { Link } from 'react-router-dom';
import { api, eventsById } from '../api';
import { Async, Badge, Empty, H1, day, money, useAsync, useUI, when } from '../ui';

export default function Orders() {
  const { ask, notify, run } = useUI();
  const s = useAsync(async () => {
    const [d, rf] = await Promise.all([api('/api/orders?size=50'), api('/api/refund-requests/mine')]);
    return { d, rf, m: await eventsById(d.content.map((o) => o.eventId)) };
  }, []);
  const refund = (o, i) => run(async () => {
    const r = await ask('Pedir devolución', { label: 'Motivo', req: true, ok: 'Enviar solicitud' });
    if (!r) return;
    await api(`/api/orders/${o}/items/${i}/refund-requests`, { method: 'POST', body: { reason: r } });
    notify('Solicitud enviada. Te avisamos cuando se resuelva.'); s.reload();
  });
  const cancel = (id) => run(async () => {
    if (!(await ask('¿Cancelar la reserva?', { ok: 'Cancelar reserva' }))) return;
    await api('/api/orders/' + id, { method: 'DELETE' }); notify('Reserva cancelada.'); s.reload();
  });
  return (
    <>
      <H1>Mis compras</H1>
      <Async s={s}>{({ d, rf, m }) => (
        <>
          {d.content.length ? d.content.map((o) => (
            <article className="panel" key={o.orderId}>
              <div className="sh"><h2>{m[o.eventId]?.name || 'Evento'}</h2><Badge s={o.status} /></div>
              <p>{when(o.eventStartsAt)}</p>
              <ul className="lines">{o.items.map((i) => (
                <li key={i.itemId}><span>{i.seatLabel || 'Entrada general'} {i.status !== 'ACTIVE' && <Badge s={i.status} />}</span>
                  {o.status === 'CONFIRMED' && i.status === 'ACTIVE' ? <button className="btn ghost sm" onClick={() => refund(o.orderId, i.itemId)}>Pedir devolución</button> : <span>{money(i.unitPrice, i.currency)}</span>}</li>
              ))}</ul>
              <div className="actions"><b className="tot">{money(o.total, o.currency)}</b>
                <Link className="btn ghost sm" to={`/checkout/${o.orderId}`}>{o.status === 'PENDING_PAYMENT' ? 'Continuar pago' : 'Ver detalle y entradas'}</Link>
                {o.status === 'PENDING_PAYMENT' && <button className="btn ghost sm" onClick={() => cancel(o.orderId)}>Cancelar</button>}</div>
            </article>
          )) : <Empty title="Todavía no hiciste compras" text="Elegí un evento y reservá tus lugares." to="/events" cta="Explorar eventos" />}
          {!!rf.content.length && (
            <><h2>Mis devoluciones</h2><div className="scroll"><table><caption className="mut">Devoluciones solicitadas</caption>
              <thead><tr><th>Fecha</th><th>Motivo</th><th>Estado</th></tr></thead>
              <tbody>{rf.content.map((r) => <tr key={r.refundRequestId}><td>{day(r.createdAt)}</td><td>{r.reason}</td><td><Badge s={r.status} /></td></tr>)}</tbody></table></div></>
          )}
        </>
      )}</Async>
    </>
  );
}
