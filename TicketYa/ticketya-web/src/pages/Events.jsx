import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Ticket, Word } from '../Hero';
import { Async, Badge, Barcode, Empty, TYPES, hue, money, useAsync, when } from '../ui';

// Tarjeta tipo entrada: color propio por evento, muesca de corte y código de barras en el talón.
export const EventCard = ({ e, to = `/events/${e.id}`, cta = 'Ver entradas' }) => {
  const d = new Date(e.startsAt);
  const mes = d.toLocaleString('es-AR', { month: 'short' }).replace('.', '').toUpperCase();
  return (
    <li>
      <Link className="tk" style={hue(e.id)} to={to}>
        <div className="tk-main">
          <p className="tk-kind"><span>{TYPES[e.type] || e.type}</span>{e.status !== 'PUBLISHED' && <Badge s={e.status} />}</p>
          <h3>{e.name}</h3>
          <p className="tk-meta">{e.city} · {when(e.startsAt)}</p>
          {!!(e.participants || []).length && <p className="tk-meta">{e.participants.map((p) => p.name).join(', ')}</p>}
          <p className="tk-price">{e.minPrice ? <>Desde <b>{money(e.minPrice)}</b></> : 'Precio a confirmar'}</p>
        </div>
        <div className="tk-stub">
          <span className="tk-day"><b>{d.getDate()}</b>{mes}</span>
          <Barcode seed={e.id} />
          <span className="go">{cta}</span>
        </div>
      </Link>
    </li>
  );
};

export const Pager = ({ d, onPage }) => (
  <nav className="pager" aria-label="Paginación">
    <button className="btn ghost" disabled={d.page < 1} onClick={() => onPage(d.page - 1)}>Anterior</button>
    <span>Página {d.page + 1} de {Math.max(d.totalPages, 1)}</span>
    <button className="btn ghost" disabled={d.page + 1 >= d.totalPages} onClick={() => onPage(d.page + 1)}>Siguiente</button>
  </nav>
);

const Opts = ({ o }) => Object.entries(o).map(([k, t]) => <option key={k} value={k}>{t}</option>);

export default function Events() {
  const [sp, setSp] = useSearchParams();
  const q = Object.fromEntries(sp);
  const s = useAsync(() => api('/api/events?' + new URLSearchParams({ size: 12, ...q })), [sp.toString()]);
  const { role } = useAuth();
  const submit = (e) => { e.preventDefault(); setSp(Object.fromEntries([...new FormData(e.target)].filter(([, v]) => v))); };
  return (
    <>
      <section className="hero" aria-labelledby="hero-t">
        <Word />
        <Ticket />
        <Word front />
        <div className="hero-l">
          <p className="eyebrow rv" data-now>Entradas y eventos en vivo</p>
          <h1 id="hero-t" tabIndex={-1} className="rv" data-now>Viví lo que viene. <em>Sin vueltas.</em></h1>
          <p className="lead rv" data-now>Conciertos, teatro, deportes y festivales. Elegí tu lugar, pagá protegido y llevá tu entrada en el celular.</p>
          <div className="cta rv" data-now>
            <a className="btn" href="#buscar">Buscar eventos</a>
            <Link className="btn ghost" to={role ? '/tickets' : '/login'}>{role ? 'Mis entradas' : 'Ingresar'}</Link>
          </div>
          <ul className="specs rv" data-now><li><span>Pago</span><b>Protegido</b></li><li><span>Entrada</span><b>Código QR</b></li><li><span>Devoluciones</span><b>Gestionadas</b></li></ul>
        </div>
      </section>
      <div className="search-band" id="buscar">
        <div className="wrap">
          <form className="search" role="search" aria-label="Buscar eventos" onSubmit={submit} key={sp.toString()}>
            <label>Buscar<input type="search" name="q" defaultValue={q.q} placeholder="Artista o evento" /></label>
            <label>Ciudad<input name="city" defaultValue={q.city} placeholder="Ej: Tandil" /></label>
            <label>Tipo<select name="type" defaultValue={q.type || ''}><option value="">Todos</option><Opts o={TYPES} /></select></label>
            <label>Ordenar por<select name="sort" defaultValue={q.sort || 'date'}><Opts o={{ date: 'Fecha', price: 'Precio', relevance: 'Relevancia' }} /></select></label>
            <button className="btn">Buscar</button>
          </form>
        </div>
      </div>
      <div className="wrap page">
        <div className="sechead"><h2 className="sec">Próximos eventos</h2><p className="meta">§01 / Catálogo</p></div>
        <Async s={s}>{(d) => d.content.length
          ? <><ul className="grid">{d.content.map((e) => <EventCard key={e.id} e={e} />)}</ul><Pager d={d} onPage={(p) => setSp({ ...q, page: p })} /></>
          : <Empty title="No encontramos eventos" text="Probá con otra ciudad o quitá algún filtro." />}</Async>
      </div>
    </>
  );
}
