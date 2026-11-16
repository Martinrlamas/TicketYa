import { useNavigate } from 'react-router-dom';
import { ROLES, useAuth } from '../auth';
import { H1 } from '../ui';

export default function Login() {
  const { role, login, logout } = useAuth();
  const nav = useNavigate();
  return (
    <>
      <H1 sub="Elegí el rol con el que querés usar la plataforma. Por ahora el acceso se identifica por rol, sin contraseña.">Ingresá a TicketYa</H1>
      <ul className="roles">
        {Object.entries(ROLES).map(([k, r]) => (
          <li key={k}><button className="role" aria-current={role === k || undefined} onClick={() => { login(k); nav(r.home); }}><b>{r.n}</b><span>{r.d}</span></button></li>
        ))}
      </ul>
      <p><button className="btn ghost" onClick={() => { logout(); nav('/events'); }}>Seguir como visitante</button></p>
    </>
  );
}
