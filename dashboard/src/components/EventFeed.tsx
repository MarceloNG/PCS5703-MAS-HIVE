import { useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useHiveStore } from '../lib/store'
import { Terminal } from 'lucide-react'

const eventColor: Record<string, string> = {
  bid_placed: 'text-purple-400',
  auction_won: 'text-amber-400',
  auction_lost: 'text-slate-500',
  task_delegated: 'text-cyan-400',
  collect_started: 'text-cyan-300',
  block_collected: 'text-sky-400',
  arrived_meeting: 'text-amber-300',
  task_received: 'text-purple-300',
  connect_initiated: 'text-pink-400',
  connect_success: 'text-emerald-400',
  connect_fail: 'text-red-400',
  submit_attempt: 'text-emerald-300',
  submit_success: 'text-emerald-500',
  submit_fail: 'text-red-400',
  resubmit: 'text-yellow-400',
  task_finalized: 'text-slate-400',
  score_update: 'text-emerald-400',
  step_update: 'text-slate-600',
  squad_update: 'text-cyan-400',
  deactivated: 'text-red-500',
  reactivated: 'text-emerald-500',
  clear_warning: 'text-orange-500',
  low_energy: 'text-red-400',
  arrived_dest: 'text-teal-400',
  task_phase_update: 'text-blue-400',
}

const eventEmoji: Record<string, string> = {
  bid_placed: '🎯',
  auction_won: '🏆',
  auction_lost: '❌',
  task_delegated: '📋',
  collect_started: '⛏️',
  block_collected: '📦',
  arrived_meeting: '📍',
  task_received: '📩',
  connect_initiated: '🔗',
  connect_success: '✅',
  connect_fail: '💔',
  submit_attempt: '🚀',
  submit_success: '⭐',
  submit_fail: '💥',
  resubmit: '🔁',
  task_finalized: '🏁',
  score_update: '📊',
  squad_update: '👥',
  deactivated: '💀',
  reactivated: '💚',
  clear_warning: '⚠️',
  low_energy: '🔋',
  arrived_dest: '🏠',
  task_phase_update: '🔄',
}

export function EventFeed() {
  const events = useHiveStore(s => s.events)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [events.length])

  const filteredEvents = events.filter(e => e.event !== 'step_update' && e.event !== 'agent_state')

  return (
    <div className="flex flex-col h-full">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2 mb-3">
        <Terminal className="w-3.5 h-3.5" /> Event Feed
      </h2>

      <div className="flex-1 overflow-y-auto font-mono text-[11px] space-y-0.5 pr-1">
        <AnimatePresence initial={false}>
          {filteredEvents.map(ev => (
            <motion.div
              key={ev.id}
              initial={{ opacity: 0, x: 30, scale: 0.95 }}
              animate={{ opacity: 1, x: 0, scale: 1 }}
              transition={{ duration: 0.25, type: 'spring', stiffness: 400, damping: 30 }}
              className="flex gap-2 py-0.5 leading-snug hover:bg-white/[0.02] rounded transition-colors"
            >
              <span className="text-slate-600 shrink-0 tabular-nums w-8 text-right">
                {ev.step}
              </span>
              <span className="shrink-0 w-4 text-center">
                {eventEmoji[ev.event] ?? '•'}
              </span>
              <span className={`shrink-0 ${eventColor[ev.event] ?? 'text-slate-400'}`}>
                {ev.event.replace(/_/g, ' ')}
              </span>
              <span className="text-slate-300 shrink-0 font-semibold">
                {ev.agent.replace('connectionA', 'A')}
              </span>
              <span className="text-slate-500 truncate">
                {ev.data.task ? `task:${ev.data.task}` : ''}
                {ev.data.squad ? ` sq:${ev.data.squad}` : ''}
                {ev.data.block ? ` blk:${ev.data.block}` : ''}
                {ev.data.value != null ? ` val:${ev.data.value}` : ''}
                {ev.data.result ? ` res:${ev.data.result}` : ''}
                {ev.data.target ? ` tgt:${String(ev.data.target)}` : ''}
                {ev.data.action ? ` act:${String(ev.data.action)}` : ''}
              </span>
            </motion.div>
          ))}
        </AnimatePresence>
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
