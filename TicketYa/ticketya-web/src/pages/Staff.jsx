import { useState } from 'react';
import { api } from '../api';
import { Async, Empty, H1, useAsync } from '../ui';

export default function Staff() {
  const [sel, setSel] = useState('');
  const s = useAsync(async () => {
    const d = await api('/api/events?size=50');
    const id = d.content.some((e) => e.id === sel) ? sel : d.content[0]?.id;
    return { events: d.content, id, av: id ? await api(`/api/events/${id}/availability`) : null };
  }, [sel]);
  return (
    <>
      <H1 sub="Ocupación en tiempo real de cada sector.">Control de acceso</H1>
      <Async s={s}>{({ events, id, av }) => !av ? <Empty title="No hay eventos a la venta" text="Cuando haya eventos publicados vas a ver su ocupación acá." /> : (
        <>
          <div className="filters"><label>Evento<select value={id} onChange={(e) => setSel(e.target.value)}>{events.map((e) => <option key={e.id} value={e.id}>{e.name}</option>)}</select></label></div>
          {av.sectors.map((x) => { const o = x.capacity - x.available; return (
            <section className="panel" key={x.sectorId}><div className="sh"><h2>{x.name}</h2><span>{o} de {x.capacity} ocupados</span></div>
              <progress max={x.capacity} value={o} aria-label={`Ocupación de ${x.name}`} /></section>); })}
          <p className="mut">Actualizado {new Date(av.generatedAt).toLocaleTimeString('es-AR')}. <button className="btn ghost sm" onClick={s.reload}>Actualizar</button></p>
          <p className="alert">La lectura y validación de códigos QR todavía no está expuesta por el API Gateway, por eso esta pantalla solo muestra ocupación.</p>
        </>)}</Async>
    </>
  );
}
