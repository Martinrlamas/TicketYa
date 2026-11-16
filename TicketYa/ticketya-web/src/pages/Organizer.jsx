import { useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { load, save } from '../store';
import { Async, Back, Badge, Empty, H1, ST, TYPES, money, useAsync, useUI, when } from '../ui';
import { EventCard } from './Events';

const SEED = ['5a8e1d2c-7b3f-4c9a-a1e6-0d4b7c2f9e10', '6b9f2e3d-8c4a-4dab-b2f7-1e5c8d3a0f21'];
// El gateway no ofrece listado de lugares: se recuerdan los IDs conocidos en el navegador.
const venueIds = () => load('ty-venues', SEED);
const addVenueId = (id) => { const v = venueIds(); if (!v.includes(id)) save('ty-venues', [...v, id]); };
const venueList = async () => (await Promise.all(venueIds().map((i) => api('/api/venues/' + i).catch(() => null)))).filter(Boolean);
const toPrices = (fd) => [...fd].filter(([, v]) => +v > 0).map(([sectorId, v]) => ({ sectorId, price: +v, currency: 'ARS' }));
const form = (e) => Object.fromEntries(new FormData(e.target));

export function MyEvents() {
  const [status, setStatus] = useState('');
  const s = useAsync(() => api('/api/events/mine?size=50' + (status ? '&status=' + status : '')), [status]);
  return (
    <>
      <H1>Mis eventos</H1>
      <div className="filters"><label>Estado<select value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Todos</option>
        {['DRAFT', 'PUBLISHED', 'SOLD_OUT', 'SUSPENDED', 'CANCELLED', 'FINISHED'].map((x) => <option key={x} value={x}>{ST[x]}</option>)}</select></label></div>
      <Async s={s}>{(d) => d.content.length
        ? <ul className="grid">{d.content.map((e) => <EventCard key={e.id} e={e} to={`/org/event/${e.id}`} cta="Gestionar" />)}</ul>
        : <Empty title="Todavía no tenés eventos" text="Creá un evento, definí precios y publicalo." to="/org/new" cta="Crear evento" />}</Async>
    </>
  );
}

function Manage({ e, v, reload }) {
  const { ask, notify, run } = useUI();
  const ref = useRef();
  const draft = e.status === 'DRAFT';
  const pr = Object.fromEntries((e.prices || []).map((p) => [p.sectorId, p.price]));
  const put = (prices) => api(`/api/events/${e.id}/sector-prices`, { method: 'PUT', body: { prices } });
  const saveP = (ev) => { ev.preventDefault(); return run(async () => {
    const p = toPrices(new FormData(ev.target)); if (!p.length) return notify('Ingresá el precio de al menos un sector.', true);
    await put(p); notify('Precios guardados.'); reload();
  }); };
  const publish = () => run(async () => {
    if (!(await ask('¿Publicar el evento?', { ok: 'Publicar' }))) return;
    const p = toPrices(new FormData(ref.current)); if (p.length) await put(p);
    await api(`/api/events/${e.id}/publish`, { method: 'POST' }); notify('Evento publicado.'); reload();
  });
  return (
    <>
      <Back to="/org">mis eventos</Back><H1>{e.name}</H1>
      <p><Badge s={e.status} /> {when(e.startsAt)}</p><p>{v?.name} {e.city}</p>
      <form className="panel" ref={ref} onSubmit={saveP}>
        <h2>Precios por sector</h2>
        {v?.sectors?.length ? v.sectors.map((s) => (
          <label key={s.id}>{s.name} ({s.capacity} lugares), precio en ARS<input name={s.id} type="number" min="1" step="1" defaultValue={pr[s.id] ?? ''} disabled={!draft} /></label>
        )) : <p className="mut">El lugar todavía no tiene sectores. Agregalos en <Link to="/org/venues">Lugares</Link>.</p>}
        {draft ? <div className="actions"><button className="btn ghost">Guardar precios</button><button type="button" className="btn" onClick={publish}>Publicar evento</button></div>
          : <p className="mut">Solo los borradores se pueden editar.</p>}
      </form>
    </>
  );
}
export function ManageEvent() {
  const { id } = useParams();
  const s = useAsync(async () => { const e = await api('/api/events/' + id); return { e, v: await api('/api/venues/' + e.venueId).catch(() => null) }; }, [id]);
  return <Async s={s}>{({ e, v }) => <Manage e={e} v={v} reload={s.reload} />}</Async>;
}

export function NewEvent() {
  const nav = useNavigate(); const { notify, run } = useUI();
  const s = useAsync(venueList, []);
  const submit = (ev) => { ev.preventDefault(); const f = form(ev); return run(async () => {
    const e = await api('/api/events', { method: 'POST', body: { name: f.name, type: f.type, venueId: f.venueId, description: f.description, startsAt: new Date(f.startsAt).toISOString(),
      participants: f.participants.split(',').map((x) => x.trim()).filter(Boolean).map((name) => ({ name, role: 'ARTIST' })) } });
    notify('Borrador creado.'); nav('/org/event/' + e.id);
  }); };
  return (
    <>
      <H1 sub="Se guarda como borrador. Después definís los precios y lo publicás.">Crear evento</H1>
      <Async s={s}>{(vs) => !vs.length ? <Empty title="Primero necesitás un lugar" text="Un evento se realiza en un lugar con sectores." to="/org/venues" cta="Ir a Lugares" /> : (
        <form className="panel" onSubmit={submit}>
          <label>Nombre<input name="name" required /></label>
          <label>Tipo<select name="type">{Object.entries(TYPES).map(([k, t]) => <option key={k} value={k}>{t}</option>)}</select></label>
          <label>Lugar<select name="venueId">{vs.map((v) => <option key={v.id} value={v.id}>{v.name} ({v.city})</option>)}</select></label>
          <label>Fecha y hora<input name="startsAt" type="datetime-local" required /></label>
          <label>Descripción<textarea name="description" rows="3" /></label>
          <label>Artistas o participantes (separados por coma)<input name="participants" placeholder="Divididos, Invitado" /></label>
          <button className="btn">Crear borrador</button>
        </form>)}</Async>
    </>
  );
}

export function Venues() {
  const { notify, run } = useUI();
  const s = useAsync(venueList, []);
  const sector = (id) => (ev) => { ev.preventDefault(); const f = form(ev); return run(async () => {
    const body = { name: f.name, type: f.type };
    if (f.type === 'NUMBERED') body.rows = f.rows.split(',').filter((x) => x.trim()).map((x) => { const [l, n] = x.split(':'); return { rowLabel: l.trim(), seatCount: +n }; });
    else body.capacity = +f.capacity;
    await api(`/api/venues/${id}/sectors`, { method: 'POST', body }); notify('Sector agregado.'); s.reload();
  }); };
  const create = (ev) => { ev.preventDefault(); const f = form(ev); return run(async () => {
    const v = await api('/api/venues', { method: 'POST', body: { ...f, latitude: +f.latitude, longitude: +f.longitude } });
    addVenueId(v.id); notify('Lugar creado.'); s.reload();
  }); };
  const add = (ev) => { ev.preventDefault(); const id = form(ev).id.trim(); return run(async () => { await api('/api/venues/' + id); addVenueId(id); notify('Lugar agregado.'); s.reload(); }); };
  return (
    <>
      <Back to="/org">mis eventos</Back>
      <H1 sub="Un lugar tiene uno o más sectores: numerados (con asientos) o generales (por capacidad).">Lugares</H1>
      <Async s={s}>{(vs) => <>
        {vs.map((v) => (
          <section className="panel" key={v.id}>
            <h2>{v.name}</h2><p className="mut">{v.address}, {v.city}</p>
            <ul className="lines">{v.sectors.map((x) => <li key={x.id}><span>{x.name}</span><span>{x.type === 'GENERAL' ? 'General' : 'Numerado'}, {x.capacity} lugares</span></li>)}{!v.sectors.length && <li className="mut">Sin sectores todavía</li>}</ul>
            <details><summary>Agregar sector</summary>
              <form onSubmit={sector(v.id)}>
                <label>Nombre<input name="name" required /></label>
                <label>Tipo<select name="type"><option value="NUMBERED">Numerado (con asientos)</option><option value="GENERAL">General (por capacidad)</option></select></label>
                <label>Capacidad (solo general)<input name="capacity" type="number" min="1" /></label>
                <label>Filas (solo numerado)<input name="rows" placeholder="A:20, B:20" /></label>
                <button className="btn ghost">Agregar sector</button>
              </form></details>
          </section>))}
        <section className="panel"><h2>Nuevo lugar</h2>
          <form onSubmit={create}>
            <label>Nombre<input name="name" required /></label><label>Dirección<input name="address" required /></label><label>Ciudad<input name="city" required /></label>
            <label>Latitud<input name="latitude" type="number" step="any" min="-90" max="90" required placeholder="-37.3217" /></label>
            <label>Longitud<input name="longitude" type="number" step="any" min="-180" max="180" required placeholder="-59.1332" /></label>
            <button className="btn">Crear lugar</button>
          </form></section>
        <section className="panel"><h2>Usar un lugar existente</h2>
          <form onSubmit={add}><label>ID del lugar<input name="id" required pattern="[0-9a-fA-F\-]{36}" /></label><button className="btn ghost">Agregar a mi lista</button></form></section>
      </>}</Async>
    </>
  );
}
