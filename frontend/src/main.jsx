import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Route, Routes } from 'react-router-dom';

import Inicio from './pages/Inicio.jsx';
import Admin from './pages/Admin.jsx';
import './estilos.css';

// HashRouter usa URLs tipo /#/admin. Funciona en GitHub Pages sin configurar el servidor
// (con BrowserRouter, recargar /admin daría 404 porque ese archivo no existe).
createRoot(document.getElementById('root')).render(
  <StrictMode>
    <HashRouter>
      <Routes>
        <Route path="/" element={<Inicio />} />
        <Route path="/admin" element={<Admin />} />
      </Routes>
    </HashRouter>
  </StrictMode>,
);
