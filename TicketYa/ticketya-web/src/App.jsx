import { useEffect } from 'react';
import { Link, NavLink, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { NAV, ROLES, useAuth } from './auth';
import { Empty, short } from './ui';
import Login from './pages/Login';
import Events from './pages/Events';
import EventDetail from './pages/EventDetail';
import Checkout from './pages/Checkout';
import Tickets from './pages/Tickets';
import Orders from './pages/Orders';
import { MyEvents, ManageEvent, NewEvent, Venues } from './pages/Organizer';
import { Refunds, Moderate } from './pages/Admin';
import Staff from './pages/Staff';
import { Brand } from './Hero';

function Guard({ role, children }) {
  const { role: r } = useAuth();
  return r === role ? children : <Empty title="Esta sección es para otro rol" text={`Necesitás ingresar como ${ROLES[role].n.toLowerCase()} para verla.`} to="/login" cta="Elegir rol" />;
}

export default function App() {
  const { role, userId } = useAuth();
  const { pathname } = useLocation();
  // Al navegar, el foco pasa al título: los lectores de pantalla anuncian la nueva vista.
  useEffect(() => { const t = setTimeout(() => (document.querySelector('main h1') || document.getElementById('main'))?.focus(), 60); window.scrollTo(0, 0); return () => clearTimeout(t); }, [pathname]);
  const g = (r, el) => <Guard role={r}>{el}</Guard>;
  return (
    <>
      <a className="skip" href="#main">Saltar al contenido</a>
      <header>
        <div className="wrap bar">
          <Link className="brand" to="/">TicketYa</Link>
          <nav aria-label="Principal"><ul>{(role ? NAV[role] : [['/events', 'Eventos']]).map(([p, t]) => <li key={p}><NavLink to={p} end>{t}</NavLink></li>)}</ul></nav>
          <div className="who">
            {role ? <><span>{ROLES[role].n}</span><small>ID {short(userId)}</small><Link className="btn ghost sm" to="/login">Cambiar rol</Link></> : <Link className="btn sm" to="/login">Ingresar</Link>}
          </div>
        </div>
      </header>
      <main id="main" tabIndex={-1}>
        <Routes>
          <Route path="events" element={<Events />} />
          <Route element={<div className="wrap page"><Outlet /></div>}>
            <Route index element={<Navigate to={role ? ROLES[role].home : '/events'} replace />} />
            <Route path="login" element={<Login />} />
            <Route path="events/:id" element={<EventDetail />} />
            <Route path="checkout/:id" element={g('CUSTOMER', <Checkout />)} />
            <Route path="tickets" element={g('CUSTOMER', <Tickets />)} />
            <Route path="orders" element={g('CUSTOMER', <Orders />)} />
            <Route path="org" element={g('ORGANIZER', <MyEvents />)} />
            <Route path="org/new" element={g('ORGANIZER', <NewEvent />)} />
            <Route path="org/venues" element={g('ORGANIZER', <Venues />)} />
            <Route path="org/event/:id" element={g('ORGANIZER', <ManageEvent />)} />
            <Route path="admin/refunds" element={g('ADMIN', <Refunds />)} />
            <Route path="admin/events" element={g('ADMIN', <Moderate />)} />
            <Route path="staff" element={g('ACCESS_STAFF', <Staff />)} />
            <Route path="*" element={<Empty title="Página no encontrada" text="La dirección no existe." to="/events" cta="Ir a eventos" />} />
          </Route>
        </Routes>
      </main>
      <footer className="close">
        <div className="close-in">
          <div>
            <h2 className="rv">Tu próxima entrada, <em>a un toque.</em></h2>
            <p className="fine">Pago protegido · Devoluciones gestionadas</p>
          </div>
          <div className="cta">
            <Link className="btn" to="/events">Explorar eventos</Link>
            <Link className="btn ghost" to={role ? '/tickets' : '/login'}>{role ? 'Mis entradas' : 'Ingresar'}</Link>
          </div>
        </div>
        <ul className="strip"><li>TicketYa</li><li>Eventos</li><li>Entradas con QR</li><li>Devoluciones</li></ul>
        <Brand className="bigword" />
      </footer>
    </>
  );
}
