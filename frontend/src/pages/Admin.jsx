import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import {
  cambiarEstado, cerrarSesion, iniciarSesion, obtenerEstadisticas, obtenerNegocio, obtenerToken, obtenerTurnos,
} from '../api.js';
import { ESTADOS, aMinutos, fechaLarga, formatearPesos, hora, hoyISO, sumarDias } from '../formato.js';
import './Admin.css';

export default function Admin() {
  const [logueado, setLogueado] = useState(Boolean(obtenerToken()));

  const salir = () => {
    cerrarSesion();
    setLogueado(false);
  };

  return (
    <div className="admin">
      <header className="barra">
        <Link className="barra__logo" to="/">Turno<b>Bot</b></Link>
        <div className="barra__acciones">
          <Link className="boton boton--borde" to="/">Ver chat</Link>
          {logueado && <button type="button" className="boton boton--borde" onClick={salir}>Salir</button>}
        </div>
      </header>
      {logueado ? <Panel alExpirar={salir} /> : <Login alEntrar={() => setLogueado(true)} />}
    </div>
  );
}

// =====================================================================
// Login
// =====================================================================

function Login({ alEntrar }) {
  const [usuario, setUsuario] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [cargando, setCargando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    setError('');
    setCargando(true);
    try {
      await iniciarSesion(usuario, password);
      alEntrar();
    } catch (err) {
      setError(err.status === 401 ? 'Usuario o contraseña incorrectos.' : err.message);
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="login">
      <form className="login__caja" onSubmit={enviar}>
        <h1>Panel del dueño</h1>
        <p className="tenue">Agenda, estados de turnos y estadísticas.</p>
        <label>
          Usuario
          <input value={usuario} onChange={(e) => setUsuario(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          Contraseña
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete="current-password" required />
        </label>
        {error && <p className="login__error" role="alert">{error}</p>}
        <button className="boton" type="submit" disabled={cargando}>{cargando ? 'Entrando…' : 'Entrar'}</button>
        <p className="login__demo">
          Demo: usuario <code>admin</code> · contraseña <code>demo1234</code>
        </p>
      </form>
    </main>
  );
}

// =====================================================================
// Panel
// =====================================================================

function Panel({ alExpirar }) {
  const [fecha, setFecha] = useState(hoyISO());
  const [negocio, setNegocio] = useState(null);
  const [turnos, setTurnos] = useState([]);
  const [stats, setStats] = useState(null);
  const [elegido, setElegido] = useState(null);
  const [error, setError] = useState('');

  // useCallback: la función se recrea solo si cambia la fecha (así el useEffect no entra en bucle)
  const cargar = useCallback(async () => {
    try {
      const [listaTurnos, estadisticas] = await Promise.all([obtenerTurnos(fecha), obtenerEstadisticas(fecha)]);
      setTurnos(listaTurnos);
      setStats(estadisticas);
      setError('');
    } catch (err) {
      if (err.status === 401) alExpirar(); // el token venció: volvemos al login
      else setError(err.message);
    }
  }, [fecha, alExpirar]);

  useEffect(() => { obtenerNegocio().then(setNegocio).catch(() => {}); }, []);
  useEffect(() => { cargar(); }, [cargar]);

  async function actualizarEstado(turno, estado) {
    const actualizado = await cambiarEstado(turno.id, estado);
    setElegido(actualizado);
    cargar();
  }

  const esHoy = fecha === hoyISO();

  return (
    <main className="panel">
      <div className="panel__cabecera">
        <div>
          <p className="etiqueta">{negocio?.nombre}</p>
          <h1 className="panel__fecha">{fechaLarga(fecha)}</h1>
        </div>
        <div className="navegador-fecha">
          <button type="button" onClick={() => setFecha(sumarDias(fecha, -1))} aria-label="Día anterior">←</button>
          <button type="button" onClick={() => setFecha(hoyISO())} disabled={esHoy}>Hoy</button>
          <button type="button" onClick={() => setFecha(sumarDias(fecha, 1))} aria-label="Día siguiente">→</button>
        </div>
      </div>

      {error && <p className="aviso" role="alert">{error}</p>}

      {stats && (
        <section className="kpis" aria-label="Resumen">
          <Kpi titulo="Turnos del día" valor={stats.turnosDelDia} />
          <Kpi titulo="Ingresos del día" valor={formatearPesos(stats.ingresosDelDia)} />
          <Kpi titulo="Ocupación" valor={`${stats.ocupacionDelDia}%`} barra={stats.ocupacionDelDia} />
          <Kpi titulo="Últimos 7 días" valor={formatearPesos(stats.ingresosSemana)}
               detalle={`${plural(stats.turnosSemana, 'turno')} · ${plural(stats.cancelacionesSemana, 'cancelado')}`} />
        </section>
      )}

      <div className="panel__cuerpo">
        {negocio && (
          <Agenda negocio={negocio} turnos={turnos} esHoy={esHoy}
                  elegidoId={elegido?.id} alElegir={setElegido} />
        )}

        <aside className="panel__lateral">
          {elegido ? (
            <DetalleTurno turno={elegido} alCambiar={actualizarEstado} alCerrar={() => setElegido(null)} />
          ) : (
            <div className="caja caja--vacia">Tocá un turno de la agenda para ver el detalle y marcarlo.</div>
          )}
          {stats && <Ranking titulo="Más pedidos (7 días)" items={stats.serviciosMasPedidos} />}
          {stats && <Ranking titulo="Turnos por barbero (7 días)" items={stats.turnosPorBarbero} />}
        </aside>
      </div>
    </main>
  );
}

/** plural(1, 'turno') → "1 turno" · plural(3, 'turno') → "3 turnos" */
const plural = (n, palabra) => `${n} ${palabra}${n === 1 ? '' : 's'}`;

function Kpi({ titulo, valor, detalle, barra }) {
  return (
    <div className="kpi">
      <span className="kpi__titulo">{titulo}</span>
      <strong className="kpi__valor">{valor}</strong>
      {detalle && <span className="kpi__detalle">{detalle}</span>}
      {barra !== undefined && (
        <span className="kpi__barra"><span style={{ width: `${Math.min(barra, 100)}%` }} /></span>
      )}
    </div>
  );
}

// =====================================================================
// Agenda: una columna por barbero, los turnos posicionados según su hora
// =====================================================================

const PX_POR_MINUTO = 1.4;

function Agenda({ negocio, turnos, esHoy, elegidoId, alElegir }) {
  const apertura = aMinutos(negocio.apertura);
  const cierre = aMinutos(negocio.cierre);
  const alto = (cierre - apertura) * PX_POR_MINUTO;
  const horas = [];
  for (let m = apertura; m <= cierre; m += 60) horas.push(m);

  const ahora = new Date();
  const minutoActual = ahora.getHours() * 60 + ahora.getMinutes();
  const mostrarAhora = esHoy && minutoActual >= apertura && minutoActual <= cierre;

  if (turnos.length === 0 && !negocio.barberos.length) return null;

  return (
    <section className="agenda caja" aria-label="Agenda del día">
      <div className="agenda__encabezado" style={{ '--columnas': negocio.barberos.length }}>
        <span />
        {negocio.barberos.map((b) => <strong key={b.id}>{b.nombre}</strong>)}
      </div>

      <div className="agenda__scroll">
        <div className="agenda__grilla" style={{ '--columnas': negocio.barberos.length, height: alto }}>
          <div className="agenda__horas">
            {horas.map((m) => (
              <span key={m} style={{ top: (m - apertura) * PX_POR_MINUTO }}>
                {String(m / 60).padStart(2, '0')}:00
              </span>
            ))}
          </div>

          {negocio.barberos.map((barbero) => (
            <div key={barbero.id} className="agenda__columna">
              {turnos.filter((t) => t.barberoId === barbero.id).map((t) => {
                const inicio = aMinutos(hora(t.inicio)) - apertura;
                const duracion = aMinutos(hora(t.fin)) - aMinutos(hora(t.inicio));
                return (
                  <button
                    key={t.id}
                    type="button"
                    className={`bloque bloque--${t.estado.toLowerCase()}${duracion < 30 ? ' bloque--corto' : ''}${t.id === elegidoId ? ' bloque--elegido' : ''}`}
                    style={{ top: inicio * PX_POR_MINUTO, height: duracion * PX_POR_MINUTO - 3 }}
                    onClick={() => alElegir(t)}
                    title={`${hora(t.inicio)} · ${t.servicioNombre} · ${t.clienteNombre}`}
                  >
                    <span className="bloque__hora">{hora(t.inicio)}</span>
                    <span className="bloque__texto">{t.clienteNombre} · {t.servicioNombre}</span>
                  </button>
                );
              })}
            </div>
          ))}

          {mostrarAhora && (
            <div className="agenda__ahora" style={{ top: (minutoActual - apertura) * PX_POR_MINUTO }} aria-hidden="true" />
          )}
        </div>
      </div>

      {turnos.length === 0 && <p className="agenda__vacia">No hay turnos este día.</p>}
    </section>
  );
}

function DetalleTurno({ turno, alCambiar, alCerrar }) {
  const acciones = [
    ['COMPLETADO', 'Completado'],
    ['AUSENTE', 'No vino'],
    ['CANCELADO', 'Cancelar'],
    ['CONFIRMADO', 'Volver a confirmado'],
  ].filter(([estado]) => estado !== turno.estado);

  return (
    <div className="caja detalle">
      <div className="detalle__cabecera">
        <span className={`estado estado--${turno.estado.toLowerCase()}`}>{ESTADOS[turno.estado]}</span>
        <button type="button" className="detalle__cerrar" onClick={alCerrar} aria-label="Cerrar detalle">×</button>
      </div>
      <h2>{turno.clienteNombre}</h2>
      <dl>
        <dt>Servicio</dt><dd>{turno.servicioNombre} · {formatearPesos(turno.precio)}</dd>
        <dt>Horario</dt><dd>{hora(turno.inicio)} a {hora(turno.fin)} con {turno.barberoNombre}</dd>
        <dt>Celular</dt><dd><a href={`https://wa.me/549${turno.clienteTelefono}`} target="_blank" rel="noreferrer">{turno.clienteTelefono}</a></dd>
        <dt>Código</dt><dd>#{turno.id}</dd>
      </dl>
      <div className="detalle__acciones">
        {acciones.map(([estado, texto]) => (
          <button key={estado} type="button" className={`boton boton--chico${estado === 'CANCELADO' ? ' boton--peligro' : ''}`}
                  onClick={() => alCambiar(turno, estado)}>
            {texto}
          </button>
        ))}
      </div>
    </div>
  );
}

function Ranking({ titulo, items }) {
  const maximo = Math.max(1, ...items.map((i) => i.cantidad));
  return (
    <div className="caja ranking">
      <h3>{titulo}</h3>
      <ul>
        {items.map((i) => (
          <li key={i.nombre}>
            <span>{i.nombre}</span>
            <span className="ranking__barra"><span style={{ width: `${(i.cantidad / maximo) * 100}%` }} /></span>
            <span className="ranking__numero">{i.cantidad}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
