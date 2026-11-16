import { QRCodeSVG } from 'qrcode.react';
import { api, eventsById } from '../api';
import { Async, Badge, Barcode, Empty, H1, day, hue, short, useAsync } from '../ui';

// E-ticket: bloque de color con el nombre del evento, borde de corte festoneado y datos debajo.
// El QR siempre es oscuro sobre blanco para que se pueda escanear.
const hora = (d) => new Date(d).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit' });
export const Ticket = ({ t, ev }) => (
  <li className={`et${t.status === 'VALID' ? '' : ' dim'}`} style={hue(t.eventId || ev?.id || t.ticketId)}>
    <div className="et-top">
      <span className="et-tag">E-TICKET</span>
      <h3>{ev?.name || 'Evento'}</h3>
      <Barcode seed={t.ticketId} n={26} />
      <p className="et-pass">{t.seatLabel || 'Entrada general'}{ev ? ` — ${day(ev.startsAt)}` : ''}</p>
    </div>
    <div className="et-bot">
      <dl>
        <div><dt>Entrada</dt><dd>{short(t.ticketId)}</dd></div>
        <div><dt>Ubicación</dt><dd>{t.seatLabel || 'General'}</dd></div>
        <div><dt>Fecha</dt><dd>{ev ? day(ev.startsAt) : '—'}</dd></div>
        <div><dt>Hora</dt><dd>{ev ? hora(ev.startsAt) : '—'}</dd></div>
      </dl>
      <div className="qrbox" role="img" aria-label="Código QR de la entrada"><QRCodeSVG value={t.qrCode} size={120} bgColor="#ffffff" fgColor="#000000" /></div>
      <p className="et-st"><Badge s={t.status} /></p>
    </div>
  </li>
);

export default function Tickets() {
  const s = useAsync(async () => { const d = await api('/api/tickets?size=50'); return { d, m: await eventsById(d.content.map((t) => t.eventId)) }; }, []);
  return (
    <>
      <H1 sub="Mostrá el código QR en la puerta del evento.">Mis entradas</H1>
      <Async s={s}>{({ d, m }) => d.content.length
        ? <ul className="tks">{d.content.map((t) => <Ticket key={t.ticketId} t={t} ev={m[t.eventId]} />)}</ul>
        : <Empty title="Todavía no tenés entradas" text="Cuando compres, van a aparecer acá." to="/events" cta="Explorar eventos" />}</Async>
    </>
  );
}
