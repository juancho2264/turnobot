// Funciones chicas de formato, reutilizadas en varias pantallas.

const pesos = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 0 });

export const formatearPesos = (monto) => pesos.format(monto);

/** "2026-11-19T17:30:00" → "17:30" */
export const hora = (fechaHora) => fechaHora.slice(11, 16);

/** "17:30" o "17:30:00" → minutos desde las 00:00 (1050) */
export const aMinutos = (texto) => {
  const [h, m] = texto.split(':').map(Number);
  return h * 60 + m;
};

/** Fecha de hoy como "2026-11-19", en la zona horaria del navegador. */
export function hoyISO() {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

/** Suma días a una fecha "AAAA-MM-DD" sin problemas de zona horaria. */
export function sumarDias(fechaISO, dias) {
  const d = new Date(`${fechaISO}T12:00:00`);
  d.setDate(d.getDate() + dias);
  return d.toISOString().slice(0, 10);
}

/** "2026-11-19" → "Jueves 19 de noviembre" (solo la primera letra en mayúscula) */
export function fechaLarga(fechaISO) {
  const texto = new Date(`${fechaISO}T12:00:00`).toLocaleDateString('es-AR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export const ESTADOS = {
  CONFIRMADO: 'Confirmado',
  COMPLETADO: 'Completado',
  CANCELADO: 'Cancelado',
  AUSENTE: 'No vino',
};
