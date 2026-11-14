import { useEffect, useRef, useState } from 'react';

import { enviarMensaje } from '../api.js';
import { fechaLarga, formatearPesos, hora } from '../formato.js';
import './Chat.css';

const nuevaSesion = () => crypto.randomUUID();
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Chat estilo WhatsApp. El estado es una lista de mensajes:
 * { id, de: 'bot' | 'yo', texto, opciones?, turno? }
 */
export default function Chat({ nombreNegocio }) {
  const [mensajes, setMensajes] = useState([]);
  const [texto, setTexto] = useState('');
  const [escribiendo, setEscribiendo] = useState(false);
  const sesion = useRef(nuevaSesion());
  const fin = useRef(null);
  const contador = useRef(0);

  const agregar = (mensaje) =>
    setMensajes((anteriores) => [...anteriores, { id: ++contador.current, ...mensaje }]);

  // Si el usuario manda algo mientras el bot escribe, lo guardamos y lo enviamos al terminar
  const pendiente = useRef(null);
  const ocupado = useRef(false); // ref y no state: se actualiza al instante, sin esperar un render

  async function hablar(contenido, { mostrar = true } = {}) {
    const limpio = contenido.trim();
    if (!limpio) return;
    setTexto('');
    if (ocupado.current) {
      pendiente.current = limpio;
      return;
    }
    ocupado.current = true;
    if (mostrar) agregar({ de: 'yo', texto: limpio });
    setEscribiendo(true);
    try {
      // Pedimos la respuesta y esperamos un mínimo para que el "escribiendo..." se vea natural
      const [respuesta] = await Promise.all([enviarMensaje(sesion.current, limpio), esperar(450)]);
      agregar({ de: 'bot', ...respuesta });
    } catch (error) {
      agregar({
        de: 'bot',
        texto: `${error.message} Si recién abriste la demo, el servidor puede tardar unos segundos en despertarse.`,
        opciones: ['Hola'],
        error: true,
      });
    } finally {
      ocupado.current = false;
      setEscribiendo(false);
      if (pendiente.current) {
        const siguiente = pendiente.current;
        pendiente.current = null;
        hablar(siguiente);
      }
    }
  }

  // Al abrir el chat, el bot saluda (le mandamos "hola" sin mostrarlo).
  // El ref evita saludar dos veces: en desarrollo, StrictMode ejecuta los efectos dos veces a propósito.
  const saludado = useRef(false);
  useEffect(() => {
    if (saludado.current) return;
    saludado.current = true;
    hablar('hola', { mostrar: false });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Bajar al último mensaje cada vez que llega uno nuevo
  useEffect(() => {
    fin.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [mensajes, escribiendo]);

  function reiniciar() {
    sesion.current = nuevaSesion();
    setMensajes([]);
    hablar('hola', { mostrar: false });
  }

  const ultimo = mensajes.at(-1);

  return (
    <section className="chat" aria-label="Chat para sacar turno">
      <header className="chat__header">
        <div className="chat__avatar" aria-hidden="true">💈</div>
        <div className="chat__titulo">
          <strong>{nombreNegocio}</strong>
          <span>{escribiendo ? 'escribiendo…' : 'en línea · responde al instante'}</span>
        </div>
        <button type="button" className="chat__reiniciar" onClick={reiniciar} title="Empezar de nuevo">
          ↻
        </button>
      </header>

      <div className="chat__mensajes" role="log" aria-live="polite">
        {mensajes.map((m) => (
          <div key={m.id} className={`burbuja burbuja--${m.de}${m.error ? ' burbuja--error' : ''}`}>
            <p>{m.texto}</p>
            {m.turno && <TarjetaTurno turno={m.turno} />}
          </div>
        ))}
        {escribiendo && (
          <div className="burbuja burbuja--bot burbuja--escribiendo" aria-label="El bot está escribiendo">
            <span /><span /><span />
          </div>
        )}
        <div ref={fin} />
      </div>

      {/* Respuestas rápidas: solo las del último mensaje del bot */}
      {!escribiendo && ultimo?.de === 'bot' && ultimo.opciones?.length > 0 && (
        <div className="chat__opciones">
          {ultimo.opciones.map((opcion) => (
            <button key={opcion} type="button" onClick={() => hablar(opcion)}>
              {opcion}
            </button>
          ))}
        </div>
      )}

      <form
        className="chat__entrada"
        onSubmit={(e) => {
          e.preventDefault();
          hablar(texto);
        }}
      >
        <input
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder='Escribí, por ejemplo: "corte el viernes a la tarde"'
          aria-label="Mensaje"
          maxLength={500}
        />
        <button type="submit" disabled={!texto.trim()} aria-label="Enviar">
          ➤
        </button>
      </form>
    </section>
  );
}

function TarjetaTurno({ turno }) {
  return (
    <div className="tarjeta-turno">
      <span className="tarjeta-turno__codigo">#{turno.id}</span>
      <strong>{turno.servicioNombre}</strong>
      <span>
        {fechaLarga(turno.inicio.slice(0, 10))} · {hora(turno.inicio)} hs
      </span>
      <span>
        Con {turno.barberoNombre} · {formatearPesos(turno.precio)}
      </span>
    </div>
  );
}
