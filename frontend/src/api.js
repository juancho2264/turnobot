// Todas las llamadas al backend pasan por acá.
// Si mañana cambia la URL o el formato de errores, se toca un solo archivo.

const BASE = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
const CLAVE_TOKEN = 'turnobot.token';

export class ErrorApi extends Error {
  constructor(mensaje, status) {
    super(mensaje);
    this.status = status;
  }
}

async function pedir(ruta, { metodo = 'GET', cuerpo, conToken = false } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (conToken) {
    const token = obtenerToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let respuesta;
  try {
    respuesta = await fetch(`${BASE}${ruta}`, {
      method: metodo,
      headers,
      body: cuerpo ? JSON.stringify(cuerpo) : undefined,
    });
  } catch {
    throw new ErrorApi('No me pude conectar con el servidor.', 0);
  }

  if (!respuesta.ok) {
    // El backend responde los errores con formato ProblemDetail: { detail: "..." }
    const datos = await respuesta.json().catch(() => ({}));
    throw new ErrorApi(datos.detail ?? 'Algo salió mal.', respuesta.status);
  }
  return respuesta.json();
}

// ---- Público ----
export const enviarMensaje = (sesionId, mensaje) =>
  pedir('/api/chat', { metodo: 'POST', cuerpo: { sesionId, mensaje } });

export const obtenerNegocio = () => pedir('/api/negocio');

// ---- Login ----
export async function iniciarSesion(usuario, password) {
  const { token } = await pedir('/api/auth/login', { metodo: 'POST', cuerpo: { usuario, password } });
  // sessionStorage: el token se borra al cerrar la pestaña (más seguro que localStorage para un panel)
  sessionStorage.setItem(CLAVE_TOKEN, token);
}

export const cerrarSesion = () => sessionStorage.removeItem(CLAVE_TOKEN);
export const obtenerToken = () => sessionStorage.getItem(CLAVE_TOKEN);

// ---- Panel (requieren token) ----
export const obtenerTurnos = (fecha) => pedir(`/api/admin/turnos?fecha=${fecha}`, { conToken: true });
export const obtenerEstadisticas = (fecha) => pedir(`/api/admin/estadisticas?fecha=${fecha}`, { conToken: true });
export const cambiarEstado = (id, estado) =>
  pedir(`/api/admin/turnos/${id}/estado`, { metodo: 'PATCH', cuerpo: { estado }, conToken: true });
