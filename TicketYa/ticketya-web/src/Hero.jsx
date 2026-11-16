import { QRCodeSVG } from 'qrcode.react';

const LETTERS = 'TICKETYA'.split('');
const FRONT = 5; // la "T" de la derecha pasa por delante del sujeto; el resto queda detrás

// Dos capas idénticas (mismas métricas flex): cada una oculta las letras que no le pertenecen.
export const Word = ({ front }) => (
  <div className={`word${front ? ' front' : ''}`} aria-hidden="true" data-now>
    {LETTERS.map((c, i) => <i key={i} className={(i === FRONT) === !!front ? undefined : 'x'}>{c}</i>)}
  </div>
);

// Sujeto: entrada con muescas de corte, dibujada con degradado tonal neutro y una línea especular.
// El QR es el real de la librería (el producto emite entradas con QR).
export const Ticket = () => (
  <svg className="subject" data-now viewBox="0 0 940 420" width="940" height="420" role="img" aria-label="Entrada con código QR">
    <defs>
      <linearGradient id="tk-g" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="#FBFBFA" /><stop offset=".55" stopColor="#D9D9D6" /><stop offset="1" stopColor="#A9A9A6" />
      </linearGradient>
      <linearGradient id="tk-s" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0" stopColor="#fff" stopOpacity="0" /><stop offset=".3" stopColor="#fff" /><stop offset="1" stopColor="#fff" stopOpacity=".2" />
      </linearGradient>
    </defs>
    <path d="M0 0H658A22 22 0 0 0 702 0H940V420H702A22 22 0 0 0 658 420H0Z" fill="url(#tk-g)" />
    <path d="M0 1.5H940" stroke="url(#tk-s)" strokeWidth="3" />
    <path d="M680 34V386" stroke="#0D0D0F" strokeOpacity=".5" strokeWidth="2" strokeDasharray="3 9" strokeLinecap="round" />
    <g fill="#0D0D0F" fontFamily="'IBM Plex Mono',monospace" fontSize="15" letterSpacing="2.4">
      <text x="44" y="64">ENTRADA / ADMIT ONE</text>
      <text x="44" y="384" opacity=".7">TICKETYA · QR</text>
    </g>
    <path d="M44 130H620M44 190H620M44 250H420" stroke="#0D0D0F" strokeOpacity=".16" strokeWidth="1" />
    <path d="M44 310H620" stroke="#0D0D0F" strokeOpacity=".35" strokeWidth="1" />
    <circle cx="610" cy="82" r="5" fill="#2F5BFF" />
    <rect x="726" y="106" width="178" height="178" fill="#fff" />
    <QRCodeSVG x="738" y="118" width="154" height="154" value="TICKETYA" size={154} bgColor="#ffffff" fgColor="#0D0D0F" level="M" />
    <text x="815" y="326" textAnchor="middle" fill="#0D0D0F" fontFamily="'IBM Plex Mono',monospace" fontSize="13" letterSpacing="2.4">ESCANEAR</text>
  </svg>
);

export const Brand = ({ className }) => <span className={className}>TicketYa<span>.</span></span>;
