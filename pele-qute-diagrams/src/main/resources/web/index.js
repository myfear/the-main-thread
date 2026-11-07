import './style.css';
import { startLab } from './diagram.js';

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startLab, { once: true });
} else {
    startLab();
}
