import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

// StrictMode removed: double-mounting breaks React Three Fiber's WebGL context
createRoot(document.getElementById('root')!).render(<App />)
