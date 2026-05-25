import { motion } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import {
  Skull, HeartPulse, AlertTriangle, Battery, Rocket, X,
  Link, Unlink, Box, Flag, Trophy, ThumbsDown, Swords,
} from 'lucide-react'
import type { BattleStats as BattleStatsType } from '../lib/store'

interface StatDef {
  key: keyof BattleStatsType
  label: string
  icon: typeof Skull
  color: string
  bg: string
  glow: string
  category: 'attack' | 'defense' | 'logistics'
}

const stats: StatDef[] = [
  { key: 'submitsOk', label: 'Submits', icon: Rocket, color: 'text-emerald-400', bg: 'bg-emerald-500/10', glow: 'shadow-emerald-500/20', category: 'attack' },
  { key: 'connectsOk', label: 'Connects', icon: Link, color: 'text-pink-400', bg: 'bg-pink-500/10', glow: 'shadow-pink-500/20', category: 'attack' },
  { key: 'blocksCollected', label: 'Blocos', icon: Box, color: 'text-cyan-400', bg: 'bg-cyan-500/10', glow: 'shadow-cyan-500/20', category: 'attack' },
  { key: 'auctionsWon', label: 'Leiloes', icon: Trophy, color: 'text-amber-400', bg: 'bg-amber-500/10', glow: 'shadow-amber-500/20', category: 'attack' },
  { key: 'tasksFinalized', label: 'Tasks OK', icon: Flag, color: 'text-teal-400', bg: 'bg-teal-500/10', glow: 'shadow-teal-500/20', category: 'attack' },
  { key: 'deactivations', label: 'Mortes', icon: Skull, color: 'text-red-400', bg: 'bg-red-500/10', glow: 'shadow-red-500/20', category: 'defense' },
  { key: 'reactivations', label: 'Revives', icon: HeartPulse, color: 'text-green-400', bg: 'bg-green-500/10', glow: 'shadow-green-500/20', category: 'defense' },
  { key: 'clearWarnings', label: 'Clears', icon: AlertTriangle, color: 'text-orange-400', bg: 'bg-orange-500/10', glow: 'shadow-orange-500/20', category: 'defense' },
  { key: 'lowEnergy', label: 'Low Energy', icon: Battery, color: 'text-yellow-400', bg: 'bg-yellow-500/10', glow: 'shadow-yellow-500/20', category: 'defense' },
  { key: 'submitsFail', label: 'Sub. Fail', icon: X, color: 'text-red-300', bg: 'bg-red-400/10', glow: 'shadow-red-400/20', category: 'logistics' },
  { key: 'connectsFail', label: 'Con. Fail', icon: Unlink, color: 'text-rose-400', bg: 'bg-rose-500/10', glow: 'shadow-rose-500/20', category: 'logistics' },
  { key: 'auctionsLost', label: 'Auct. Lost', icon: ThumbsDown, color: 'text-slate-400', bg: 'bg-slate-500/10', glow: 'shadow-slate-500/20', category: 'logistics' },
]

function StatCard({ def, value }: { def: StatDef; value: number }) {
  const Icon = def.icon
  return (
    <motion.div
      className={`relative flex items-center gap-2 rounded-lg border border-white/5 ${def.bg} px-2.5 py-1.5 overflow-hidden group`}
      whileHover={{ scale: 1.05, borderColor: 'rgba(255,255,255,0.15)' }}
      transition={{ type: 'spring', stiffness: 400, damping: 20 }}
    >
      <div className={`absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 ${def.bg}`}
        style={{ boxShadow: `inset 0 0 30px ${def.glow.replace('shadow-', '').replace('/20', '')}` }} />

      <Icon className={`w-3.5 h-3.5 ${def.color} shrink-0 relative z-10`} />

      <div className="flex flex-col relative z-10 min-w-0">
        <span className="text-[9px] uppercase tracking-wider text-slate-500 leading-none">
          {def.label}
        </span>
        <motion.span
          key={value}
          className={`text-sm font-bold font-mono tabular-nums leading-tight ${def.color}`}
          initial={{ scale: 1.4, opacity: 0.5 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ duration: 0.3, type: 'spring', stiffness: 300 }}
        >
          {value}
        </motion.span>
      </div>

      {value > 0 && (
        <motion.div
          className={`absolute top-0.5 right-1 w-1.5 h-1.5 rounded-full ${def.color.replace('text-', 'bg-')}`}
          animate={{ opacity: [0.3, 1, 0.3] }}
          transition={{ duration: 2, repeat: Infinity, ease: 'easeInOut' }}
        />
      )}
    </motion.div>
  )
}

export function BattleStats() {
  const bs = useHiveStore(s => s.battleStats)
  const connected = useHiveStore(s => s.connected)
  const step = useHiveStore(s => s.step)
  const total = Object.values(bs).reduce((a, b) => a + b, 0)
  const attackStats = stats.filter(s => s.category === 'attack')
  const defenseStats = stats.filter(s => s.category === 'defense')
  const logisticsStats = stats.filter(s => s.category === 'logistics')

  return (
    <div className="flex flex-col gap-2 h-full overflow-y-auto pr-1">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2">
        <Swords className="w-3.5 h-3.5" /> Battle Stats
        {!connected && (
          <span className="text-[9px] text-red-500 font-normal normal-case tracking-normal ml-auto">
            offline
          </span>
        )}
      </h2>

      {!connected || (step === 0 && total === 0) ? (
        <div className="flex-1 flex items-center justify-center">
          <p className="text-[11px] text-slate-600 italic text-center leading-relaxed">
            {!connected
              ? 'Aguardando conexao com HIVE (ws://localhost:8765)...'
              : 'Simulacao iniciada. Dados aparecem conforme eventos ocorrem.'}
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          <div>
            <div className="text-[9px] uppercase tracking-widest text-emerald-600 mb-1 flex items-center gap-1">
              <div className="w-1 h-1 rounded-full bg-emerald-500 animate-pulse" />
              Ofensivo
            </div>
            <div className="grid grid-cols-2 xl:grid-cols-3 gap-1.5">
              {attackStats.map(s => <StatCard key={s.key} def={s} value={bs[s.key]} />)}
            </div>
          </div>

          <div>
            <div className="text-[9px] uppercase tracking-widest text-red-600 mb-1 flex items-center gap-1">
              <div className="w-1 h-1 rounded-full bg-red-500 animate-pulse" />
              Defensivo
            </div>
            <div className="grid grid-cols-2 xl:grid-cols-3 gap-1.5">
              {defenseStats.map(s => <StatCard key={s.key} def={s} value={bs[s.key]} />)}
            </div>
          </div>

          <div>
            <div className="text-[9px] uppercase tracking-widest text-slate-600 mb-1 flex items-center gap-1">
              <div className="w-1 h-1 rounded-full bg-slate-500 animate-pulse" />
              Falhas
            </div>
            <div className="grid grid-cols-2 xl:grid-cols-3 gap-1.5">
              {logisticsStats.map(s => <StatCard key={s.key} def={s} value={bs[s.key]} />)}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
