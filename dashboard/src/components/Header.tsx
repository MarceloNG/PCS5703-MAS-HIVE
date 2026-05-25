import { motion } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { Activity, Wifi, WifiOff, Box, LayoutGrid } from 'lucide-react'
import { useEffect, useState } from 'react'

interface HeaderProps {
  onToggle3D?: () => void
  is3D?: boolean
}

export function Header({ onToggle3D, is3D }: HeaderProps) {
  const { connected, step, score } = useHiveStore()
  const [clock, setClock] = useState('')

  useEffect(() => {
    const tick = () => setClock(new Date().toLocaleTimeString('pt-BR', { hour12: false }))
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [])

  return (
    <header className="flex items-center justify-between px-6 py-3 border-b border-border-dim bg-surface-card/60 backdrop-blur-md">
      <div className="flex items-center gap-3">
        <Activity className="w-6 h-6 text-neon-cyan" />
        <h1 className="text-lg font-bold tracking-wider text-neon-cyan uppercase">
          HIVE Command Center
        </h1>
      </div>

      <div className="flex items-center gap-6 text-sm font-mono">
        <motion.div
          className="flex items-center gap-2"
          animate={{ opacity: connected ? [0.5, 1, 0.5] : 1 }}
          transition={{ duration: 2, repeat: Infinity }}
        >
          {connected ? (
            <Wifi className="w-4 h-4 text-neon-green" />
          ) : (
            <WifiOff className="w-4 h-4 text-neon-red" />
          )}
          <span className={connected ? 'text-neon-green' : 'text-neon-red'}>
            {connected ? 'LIVE' : 'OFFLINE'}
          </span>
        </motion.div>

        <div className="flex items-center gap-2 text-slate-400">
          <span className="text-xs uppercase tracking-wider">Step</span>
          <span className="text-neon-cyan font-semibold text-base tabular-nums">
            {String(step).padStart(4, '0')}
          </span>
        </div>

        <div className="flex items-center gap-2 text-slate-400">
          <span className="text-xs uppercase tracking-wider">Score</span>
          <motion.span
            key={score}
            className="text-neon-green font-semibold text-base tabular-nums"
            initial={{ scale: 1.5, color: '#fbbf24', textShadow: '0 0 20px rgba(251,191,36,0.8)' }}
            animate={{ scale: 1, color: '#34d399', textShadow: '0 0 8px rgba(52,211,153,0.3)' }}
            transition={{ duration: 0.6, type: 'spring', stiffness: 200 }}
          >
            {String(score).padStart(5, '0')}
          </motion.span>
        </div>

        {onToggle3D && (
          <button
            onClick={onToggle3D}
            className={`flex items-center gap-1.5 px-3 py-1 rounded-md border transition-all text-xs uppercase tracking-wider font-semibold ${
              is3D
                ? 'border-neon-cyan/40 bg-neon-cyan/10 text-neon-cyan'
                : 'border-slate-700 bg-slate-800/50 text-slate-400 hover:border-slate-600 hover:text-slate-300'
            }`}
          >
            {is3D ? <Box className="w-3.5 h-3.5" /> : <LayoutGrid className="w-3.5 h-3.5" />}
            {is3D ? '3D' : '2D'}
          </button>
        )}

        <span className="text-slate-500 tabular-nums">{clock}</span>
      </div>
    </header>
  )
}
