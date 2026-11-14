import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import Chat from '../components/Chat.jsx';
import { obtenerNegocio } from '../api.js';
import { formatearPesos } from '../formato.js';
import './Inicio.css';

const PRUEBAS = [
  'corte y barba el viernes a la tarde',
  'tenés lugar mañana a las 6?',
  'cuánto sale el color?',
  'quiero cancelar mi turno',
];

export default function Inicio() {
  const [negocio, setNegocio] = useState(null);

  useEffect(() => {
    obtenerNegocio().then(setNegocio).catch(() => setNegocio(null));
  }, []);

  return (
    <div className="inicio">
      <header className="barra">
        <span className="barra__logo">Turno<b>Bot</b></span>
        <Link className="boton boton--borde" to="/admin">Panel del dueño →</Link>
      </header>

      <main className="inicio__grid">
        <section className="inicio__intro">
          <p className="etiqueta">Demo · {negocio?.nombre ?? 'Barbería El Faro'}</p>
          <h1>
            Tus clientes sacan turno <em>chateando</em>, a cualquier hora.
          </h1>
          <p className="inicio__bajada">
            Un asistente que entiende cómo escribe la gente, revisa la agenda real y reserva sin superponer turnos.
            El dueño ve todo en un panel con estadísticas.
          </p>
        </section>

        <section className="inicio__extra">
          <div className="pruebas">
            <p className="etiqueta">Probá escribirle</p>
            <ul>
              {PRUEBAS.map((p) => <li key={p}>“{p}”</li>)}
            </ul>
          </div>

          {negocio && (
            <div className="servicios">
              {negocio.servicios.map((s) => (
                <div key={s.id} className="servicio">
                  <span>{s.nombre}</span>
                  <span className="servicio__precio">{formatearPesos(s.precio)}</span>
                </div>
              ))}
            </div>
          )}

          <ul className="stack" aria-label="Tecnologías">
            <li>Java 17</li><li>Spring Boot</li><li>Spring Security + JWT</li>
            <li>JPA</li><li>React</li><li>JUnit</li>
          </ul>
        </section>

        <div className="telefono">
          <div className="telefono__pantalla">
            <Chat nombreNegocio={negocio?.nombre ?? 'Barbería El Faro'} />
          </div>
        </div>
      </main>

      <footer className="pie">
        Proyecto de portafolio de <a href="https://github.com/juancho2264" target="_blank" rel="noreferrer">Juan Cruz Ferrandini</a>.
        Negocio, precios y clientes son de ejemplo.
      </footer>
    </div>
  );
}
