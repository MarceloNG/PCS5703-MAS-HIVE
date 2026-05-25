import { useState } from 'react'
import { useHiveStore } from '../lib/store'
import { Brain, ChevronDown } from 'lucide-react'

const ALL_AGENTS = Array.from({ length: 15 }, (_, i) => `connectionA${i + 1}`)

export function AgentBeliefs() {
  const [selected, setSelected] = useState(ALL_AGENTS[0])
  const [open, setOpen] = useState(false)
  const events = useHiveStore(s => s.events)

  const agentEvents = events
    .filter(e => e.agent === selected)
    .slice(-30)

  return (
    <div className="flex flex-col h-full">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2 mb-3">
        <Brain className="w-3.5 h-3.5" /> Agent Activity
      </h2>

      <div className="relative mb-3">
        <button
          onClick={() => setOpen(!open)}
          className="w-full flex items-center justify-between px-3 py-1.5 rounded-lg border border-border-dim bg-surface-card/50 text-sm font-mono text-slate-300 hover:border-neon-cyan/30 transition-colors"
        >
          <span>{selected.replace('connection', '')}</span>
          <ChevronDown className="w-3.5 h-3.5 text-slate-500" />
        </button>

        {open && (
          <div className="absolute z-50 top-full left-0 w-full mt-1 max-h-40 overflow-y-auto rounded-lg border border-border-dim bg-surface p-1">
            {ALL_AGENTS.map(a => (
              <button
                key={a}
                onClick={() => { setSelected(a); setOpen(false) }}
                className={`w-full text-left px-3 py-1 rounded text-xs font-mono transition-colors ${
                  a === selected ? 'bg-neon-cyan/10 text-neon-cyan' : 'text-slate-400 hover:bg-surface-hover'
                }`}
              >
                {a.replace('connection', '')}
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="flex-1 overflow-y-auto font-mono text-[11px] space-y-0.5">
        {agentEvents.length === 0 ? (
          <p className="text-xs text-slate-600 italic">Nenhum evento para este agente...</p>
        ) : (
          agentEvents.map(ev => (
            <div key={ev.id} className="flex gap-2 py-0.5 text-slate-400">
              <span className="text-slate-600 tabular-nums w-8 text-right shrink-0">{ev.step}</span>
              <span className="text-neon-cyan">{ev.event.replace(/_/g, ' ')}</span>
              {ev.data.task && <span className="text-slate-500">· {String(ev.data.task)}</span>}
            </div>
          ))
        )}
      </div>
    </div>
  )
}
