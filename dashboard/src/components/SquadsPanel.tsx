import { motion, AnimatePresence } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { Users, Crown, Box, Wrench, Shield } from 'lucide-react'

const roleIcon: Record<string, typeof Crown> = {
  leader: Crown,
  collector: Box,
  assembler: Wrench,
  sentinel: Shield,
}

const roleColor: Record<string, string> = {
  leader: 'text-neon-amber',
  collector: 'text-neon-cyan',
  assembler: 'text-neon-magenta',
  sentinel: 'text-neon-green',
}

export function SquadsPanel() {
  const squads = useHiveStore(s => s.squads)

  return (
    <div className="flex flex-col gap-3 h-full overflow-y-auto pr-1">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2">
        <Users className="w-3.5 h-3.5" /> Squads
      </h2>

      {squads.length === 0 && (
        <p className="text-xs text-slate-600 italic">Aguardando dados...</p>
      )}

      <AnimatePresence>
        {squads.map(squad => (
          <motion.div
            key={squad.id}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="rounded-lg border border-border-dim bg-surface-card/50 p-3 glow-cyan"
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-semibold text-neon-cyan">{squad.id}</span>
              {squad.task && (
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-neon-purple/20 text-neon-purple font-mono">
                  {squad.task}
                </span>
              )}
            </div>

            <div className="grid grid-cols-2 gap-1.5">
              {squad.members.map(m => {
                const Icon = roleIcon[m.role] ?? Users
                return (
                  <div
                    key={m.name}
                    className="flex items-center gap-1.5 text-[11px] font-mono"
                  >
                    <Icon className={`w-3 h-3 ${roleColor[m.role] ?? 'text-slate-400'}`} />
                    <span className="text-slate-300 truncate">{m.name.replace('connection', '')}</span>
                    {m.block && (
                      <span className="text-[9px] px-1 py-px rounded bg-neon-cyan/10 text-neon-cyan">
                        {m.block}
                      </span>
                    )}
                  </div>
                )
              })}
            </div>

            {squad.meetingPoint && (
              <div className="mt-2 text-[10px] text-slate-500 font-mono">
                MP: ({squad.meetingPoint.x}, {squad.meetingPoint.y})
              </div>
            )}
          </motion.div>
        ))}
      </AnimatePresence>
    </div>
  )
}
