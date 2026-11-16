import { useState } from 'react';
import { api } from '../api';
import { load, save } from '../store';
import { Async, Badge, Empty, H1, ST, day, short, useAsync, useUI, when } from '../ui';

export function Refunds() {
  const { ask, notify, run } = useUI();
  const [status, setStatus] = useState('PENDING');
  const s = useAsync(() => api('/api/refund-requests?size=50' + (status ? '&status=' + status : '')), [status]);
  const decide = (id, approve) => run(async () => {
    const r = await ask(approve ? '¿Aprobar la devolución?' : 'Rechazar devolución', approve ? { ok: 'Aprobar' } : { label: 'Motivo del rechazo', req: true, ok: 'Rechazar' });
    if (!r) return;
    await api(`/api/refund-requests/${id}/${approve ? 'approve' : 'reject'}`, { method: 'POST', body: approve ? undefined : { reason: r } });
    notify(approve ? 'Devolución aprobada. Se está procesando.' : 'Devolución rechazada.'); s.reload();
  });
  return (
    <>
      <H1 sub="Solicitudes de los clientes, de la más antigua a la más reciente.">Devoluciones</H1>
      <div className="filters"><label>Estado<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Todos</option>
        {['PENDING', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED'].map((x) => <option key={x} value={x}>{ST[x]}</option>)}</select></label></div>
      <Async s={s}>{(d) => d.content.length ? (
        <div className="scroll"><table><caption className="mut">Solicitudes de devolución</caption>
          <thead><tr><th>Fecha</th><th>Motivo</th><th>Cliente</th><th>Orden</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{d.content.map((r) => (
            <tr key={r.refundRequestId}><td>{day(r.createdAt)}</td><td>{r.reason}</td><td>{short(r.customerId)}</td><td>{short(r.orderId)}</td><td><Badge s={r.status} /></td>
              <td>{r.status === 'PENDING' && <div className="actions"><button className="btn sm" onClick={() => decide(r.refundRequestId, true)}>Aprobar</button><button className="btn ghost sm" onClick={() => decide(r.refundRequestId, false)}>Rechazar</button></div>}</td></tr>))}</tbody></table></div>
      ) : <Empty title="No hay solicitudes" text="No hay devoluciones con este estado." />}</Async>
    </>
  );
}

const ACTIONS = { PUBLISHED: ['suspend', 'cancel'], SOLD_OUT: ['suspend', 'cancel'], SUSPENDED: ['resume', 'cancel'], DRAFT: ['cancel'] };
const LABEL = { suspend: 'Suspender', resume: 'Reactivar', cancel: 'Cancelar' };

export function Moderate() {
  const { ask, notify, run } = useUI();
  // Los eventos suspendidos no salen en la búsqueda pública: se recuerdan los IDs gestionados.
  const s = useAsync(async () => {
    const d = await api('/api/events?size=50');
    const seen = (await Promise.all(load('ty-seen', []).map((i) => api('/api/events/' + i).catch(() => null)))).filter((e) => e && !d.content.some((x) => x.id === e.id));
    return [...d.content, ...seen];
  }, []);
  const remember = (id) => { const v = load('ty-seen', []); if (!v.includes(id)) save('ty-seen', [...v, id]); };
  const mod = (e, op) => run(async () => {
    const r = await ask(`${LABEL[op]} evento`, { label: 'Motivo', req: true, ok: `${LABEL[op]} evento` }); if (!r) return;
    await api(`/api/events/${e.id}/${op}`, { method: 'POST', body: { reason: r } }); remember(e.id); notify('Listo: el estado del evento se actualizó.'); s.reload();
  });
  const lookup = (ev) => { ev.preventDefault(); const id = new FormData(ev.target).get('id').trim(); return run(async () => { await api('/api/events/' + id); remember(id); s.reload(); }); };
  return (
    <>
      <H1 sub="Suspendé, reactivá o cancelá eventos. Cancelar dispara los reintegros.">Moderación de eventos</H1>
      <form className="filters" onSubmit={lookup}><label>Buscar evento por ID (incluye borradores y suspendidos)<input name="id" required pattern="[0-9a-fA-F\-]{36}" /></label><button className="btn ghost">Agregar a la lista</button></form>
      <Async s={s}>{(all) => all.length ? (
        <div className="scroll"><table><caption className="mut">Eventos</caption>
          <thead><tr><th>Evento</th><th>Fecha</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{all.map((e) => (
            <tr key={e.id}><td><b>{e.name}</b><br /><small className="mut">{e.city}</small></td><td>{when(e.startsAt)}</td><td><Badge s={e.status} /></td>
              <td><div className="actions">{(ACTIONS[e.status] || []).map((o) => <button key={o} className={`btn sm ${o === 'cancel' ? 'bad' : 'ghost'}`} onClick={() => mod(e, o)}>{LABEL[o]}</button>)}</div></td></tr>))}</tbody></table></div>
      ) : <Empty title="No hay eventos" text="Todavía no hay eventos publicados." />}</Async>
    </>
  );
}
