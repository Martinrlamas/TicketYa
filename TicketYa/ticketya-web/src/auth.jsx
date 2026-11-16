import { createContext, useContext, useState } from 'react';
import { load, save } from './store';

export const ROLES = {
  CUSTOMER: { n: 'Cliente', d: 'Descubrí eventos, comprá y llevá tus entradas.', home: '/events' },
  ORGANIZER: { n: 'Organizador', d: 'Creá lugares y eventos, definí precios y publicá.', home: '/org', id: '3d4e5f60-8192-43a4-b5b0-2b3c4d5e6f78' },
  ADMIN: { n: 'Administrador', d: 'Moderá eventos y resolvé devoluciones.', home: '/admin/refunds' },
  ACCESS_STAFF: { n: 'Control de acceso', d: 'Mirá la ocupación de cada evento en puerta.', home: '/staff' },
};
export const NAV = {
  CUSTOMER: [['/events', 'Eventos'], ['/tickets', 'Mis entradas'], ['/orders', 'Mis compras']],
  ORGANIZER: [['/org', 'Mis eventos'], ['/org/new', 'Crear evento'], ['/org/venues', 'Lugares'], ['/events', 'Catálogo']],
  ADMIN: [['/admin/refunds', 'Devoluciones'], ['/admin/events', 'Moderación'], ['/events', 'Catálogo']],
  ACCESS_STAFF: [['/staff', 'Control de acceso'], ['/events', 'Eventos']],
};

const Ctx = createContext();
export const useAuth = () => useContext(Ctx);

// El gateway (entrega 1) identifica por X-User-Id + X-User-Role; aquí se guarda ese par por rol.
export function AuthProvider({ children }) {
  const [s, setS] = useState(() => load('ty-session', { role: null, ids: {} }));
  const set = (n) => { save('ty-session', n); setS(n); };
  const login = (role) => set({ role, ids: { ...s.ids, [role]: s.ids[role] || ROLES[role].id || crypto.randomUUID() } });
  const logout = () => set({ ...s, role: null });
  return <Ctx.Provider value={{ role: s.role, userId: s.role && s.ids[s.role], login, logout }}>{children}</Ctx.Provider>;
}
