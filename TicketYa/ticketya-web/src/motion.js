// Movimiento como mejora opcional: sin esta clase (sin JS o con prefers-reduced-motion) la página ya está completa.
export function initMotion() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const root = document.documentElement;
  root.classList.add('js');

  // Reveal al entrar en pantalla: un único observer; lo que ya está visible al cargar (data-now) se revela por rAF.
  const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); } }), { rootMargin: '0px 0px -12% 0px', threshold: 0.08 });
  const scan = () => {
    document.querySelectorAll('[data-now]:not([data-seen]),.rv:not([data-seen])').forEach((el) => {
      el.setAttribute('data-seen', '');
      if (el.hasAttribute('data-now')) requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add('in')));
      else io.observe(el);
    });
  };
  new MutationObserver(scan).observe(document.body, { childList: true, subtree: true });
  scan();

  // Un solo listener pasivo, limitado por rAF: la palabra sube y el sujeto baja al salir del hero.
  let tick = false;
  const upd = () => {
    tick = false;
    const h = document.querySelector('.hero');
    const p = h ? Math.min(1, Math.max(0, window.scrollY / h.offsetHeight)) : 0;
    root.style.setProperty('--p', p.toFixed(4));
  };
  addEventListener('scroll', () => { if (!tick) { tick = true; requestAnimationFrame(upd); } }, { passive: true });
  upd();
}
