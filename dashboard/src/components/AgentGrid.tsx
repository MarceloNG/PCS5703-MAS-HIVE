import { motion } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { Cpu, MapPin, Zap, Navigation, Crown, Box, Wrench, Shield, AlertTriangle } from 'lucide-react'

const roleIcon: Record<string, typeof Cpu> = {
  squad_leader: Crown,
  collector: Box,
  assembler: Wrench,
  sentinel: Shield,
}

const roleColor: Record<string, string> = {
  squad_leader: 'border-amber-500/40 bg-amber-500/5',
  collector: 'border-cyan-500/40 bg-cyan-500/5',
  assembler: 'border-purple-500/40 bg-purple-500/5',
  sentinel: 'border-emerald-500/40 bg-emerald-500/5',
}

const roleGlow: Record<string, string> = {
  squad_leader: 'text-amber-400',
  collector: 'text-cyan-400',
  assembler: 'text-purple-400',
  sentinel: 'text-emerald-400',
}

const resultColor: Record<string, string> = {
  success: 'text-emerald-400',
  failed: 'text-red-400',
  failed_path: 'text-orange-400',
  failed_parameter: 'text-red-400',
  failed_target: 'text-red-400',
  failed_partner: 'text-orange-400',
  none: 'text-slate-600',
}

export function AgentGrid() {
  const agents = useHiveStore(s => s.agents)
  const step = useHiveStore(s => s.step)
  const sorted = Object.values(agents).sort((a, b) => {
    const order = ['squad_leader', 'assembler', 'collector', 'sentinel']
    return (order.indexOf(a.role) - order.indexOf(b.role)) || a.name.localeCompare(b.name)
  })

  if (sorted.length === 0) {
    return (
      <div className="flex flex-col h-full">
        <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2 mb-3">
          <Cpu className="w-3.5 h-3.5" /> Agents
        </h2>
        <p className="text-xs text-slate-600 italic">Aguardando agentes...</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2 mb-3">
        <Cpu className="w-3.5 h-3.5" /> Agents ({sorted.length})
      </h2>
      <div className="flex-1 overflow-y-auto grid grid-cols-3 xl:grid-cols-5 gap-2 pr-1 content-start">
        {sorted.map(ag => {
          const Icon = roleIcon[ag.role] ?? Cpu
          const stale = step - ag.lastUpdate > 5
          return (
            <motion.div
              key={ag.name}
              layout
              initial={{ opacity: 0, scale: 0.85, y: 10 }}
              animate={{ opacity: ag.active ? 1 : 0.4, scale: 1, y: 0 }}
              transition={{ type: 'spring', stiffness: 300, damping: 25, delay: 0.02 * sorted.indexOf(ag) }}
              whileHover={{ scale: 1.04, transition: { duration: 0.15 } }}
              className={`rounded-lg border p-2.5 font-mono text-[10px] leading-tight ${roleColor[ag.role] ?? 'border-slate-700 bg-slate-800/30'} ${!ag.active ? 'grayscale' : ''}`}
            >
              <div className="flex items-center justify-between mb-1.5">
                <div className="flex items-center gap-1">
                  <Icon className={`w-3 h-3 ${roleGlow[ag.role] ?? 'text-slate-400'}`} />
                  <span className="text-slate-200 font-semibold truncate">
                    {ag.name.replace('connectionA', 'A')}
                  </span>
                </div>
                {!ag.active && <AlertTriangle className="w-3 h-3 text-red-500 animate-pulse" />}
              </div>

              <div className="space-y-0.5 text-slate-400">
                <div className="flex items-center gap-1">
                  <MapPin className="w-2.5 h-2.5 text-slate-500" />
                  <span>({ag.x},{ag.y})</span>
                  {ag.destX != null && (
                    <>
                      <Navigation className="w-2.5 h-2.5 text-cyan-600 ml-1" />
                      <span className="text-cyan-500">({ag.destX},{ag.destY})</span>
                    </>
                  )}
                </div>

                <div className="flex items-center gap-1">
                  <Zap className={`w-2.5 h-2.5 ${ag.energy > 30 ? 'text-emerald-500' : ag.energy > 10 ? 'text-amber-500' : 'text-red-500'}`} />
                  <div className="flex-1 h-1.5 rounded-full bg-slate-800 overflow-hidden">
                    <motion.div
                      className={`h-full rounded-full ${ag.energy > 30 ? 'bg-emerald-500' : ag.energy > 10 ? 'bg-amber-500' : 'bg-red-500'}`}
                      initial={false}
                      animate={{ width: `${Math.max(0, Math.min(100, ag.energy))}%` }}
                      transition={{ duration: 0.3 }}
                    />
                  </div>
                  <span className="tabular-nums w-5 text-right">{ag.energy}</span>
                </div>

                <div className="flex items-center gap-1 mt-0.5">
                  <span className="text-slate-500">act:</span>
                  <span className="text-slate-300 truncate">{ag.action}</span>
                  <span className={`${resultColor[ag.result] ?? 'text-slate-500'}`}>
                    {ag.result !== 'none' && ag.result !== 'success' ? `✗${ag.result.replace('failed_', '')}` : ag.result === 'success' ? '✓' : ''}
                  </span>
                </div>
              </div>

              {stale && (
                <div className="mt-1 text-[9px] text-slate-600 italic">stale ({step - ag.lastUpdate}s ago)</div>
              )}
            </motion.div>
          )
        })}
      </div>
    </div>
  )
}
