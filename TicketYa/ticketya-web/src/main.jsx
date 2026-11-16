import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth';
import { UIProvider } from './ui';
import './styles.css';
import { initMotion } from './motion';

initMotion();

createRoot(document.getElementById('root')).render(
  <StrictMode><BrowserRouter><AuthProvider><UIProvider><App /></UIProvider></AuthProvider></BrowserRouter></StrictMode>
);
