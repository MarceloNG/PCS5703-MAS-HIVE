import { motion } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { ListChecks } from 'lucide-react'
import type { TaskPhase } from '../lib/types'

const phases: TaskPhase[] = ['auction', 'collect', 'meet', 'connect', 'submit', 'done']

const phaseLabel: Record<TaskPhase, string> = {
  auction: 'LEILÃO',
  collect: 'COLETA',
  meet: 'MEETING',
  connect: 'CONNECT',
  submit: 'SUBMIT',
  done: 'DONE',
}

const phaseColor: Record<TaskPhase, string> = {
  auction: 'bg-neon-purple',
  collect: 'bg-neon-cyan',
  meet: 'bg-neon-amber',
  connect: 'bg-neon-magenta',
  submit: 'bg-neon-green',
  done: 'bg-emerald-500',
}

export function TaskPipeline() {
  const tasks = useHiveStore(s => s.tasks)

  return (
    <div className="flex flex-col gap-3 h-full overflow-y-auto pr-1">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2">
        <ListChecks className="w-3.5 h-3.5" /> Task Pipeline
      </h2>

      {tasks.length === 0 && (
        <p className="text-xs text-slate-600 italic">Nenhuma task ativa...</p>
      )}

      {tasks.map(task => {
        const phaseIdx = phases.indexOf(task.phase)
        const pct = task.phase === 'done' ? 100 : Math.round(((phaseIdx + task.progress / 100) / phases.length) * 100)

        return (
          <motion.div
            key={task.name}
            initial={{ opacity: 0, y: 15 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ type: 'spring', stiffness: 300, damping: 25 }}
            whileHover={{ scale: 1.01 }}
            className="rounded-lg border border-border-dim bg-surface-card/50 p-3"
          >
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-sm font-mono font-semibold text-slate-200">{task.name}</span>
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-semibold uppercase tracking-wider ${phaseColor[task.phase]}/20 text-white`}>
                {phaseLabel[task.phase]}
              </span>
            </div>

            {task.squad && (
              <div className="text-[10px] text-slate-500 font-mono mb-2">
                Squad: {task.squad}
                {task.reward != null && ` · R${task.reward}`}
                {task.deadline != null && ` · DL:${task.deadline}`}
              </div>
            )}

            <div className="w-full h-2 rounded-full bg-slate-800 overflow-hidden">
              <motion.div
                className={`h-full rounded-full ${phaseColor[task.phase]}`}
                initial={{ width: 0 }}
                animate={{ width: `${pct}%` }}
                transition={{ duration: 0.5, ease: 'easeOut' }}
              />
            </div>

            <div className="flex justify-between mt-1.5 text-[9px] font-mono text-slate-600">
              {phases.map((p, i) => (
                <span
                  key={p}
                  className={i <= phaseIdx ? 'text-neon-cyan' : ''}
                >
                  {phaseLabel[p].slice(0, 3)}
                </span>
              ))}
            </div>
          </motion.div>
        )
      })}
    </div>
  )
}
