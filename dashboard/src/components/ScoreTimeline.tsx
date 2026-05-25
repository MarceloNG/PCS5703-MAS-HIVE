import { useHiveStore } from '../lib/store'
import { LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'
import { TrendingUp } from 'lucide-react'

export function ScoreTimeline() {
  const scoreHistory = useHiveStore(s => s.scoreHistory)

  return (
    <div className="flex flex-col h-full">
      <h2 className="text-xs font-semibold uppercase tracking-widest text-slate-500 flex items-center gap-2 mb-2">
        <TrendingUp className="w-3.5 h-3.5" /> Score Timeline
      </h2>

      <div className="flex-1 min-h-0">
        {scoreHistory.length < 2 ? (
          <div className="flex items-center justify-center h-full text-xs text-slate-600 italic">
            Aguardando pontuação...
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={scoreHistory} margin={{ top: 5, right: 10, bottom: 0, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(34,211,238,0.07)" />
              <XAxis
                dataKey="step"
                tick={{ fill: '#475569', fontSize: 10, fontFamily: 'JetBrains Mono' }}
                axisLine={{ stroke: 'rgba(34,211,238,0.1)' }}
              />
              <YAxis
                tick={{ fill: '#475569', fontSize: 10, fontFamily: 'JetBrains Mono' }}
                axisLine={{ stroke: 'rgba(34,211,238,0.1)' }}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#0a0a0f',
                  border: '1px solid rgba(34,211,238,0.3)',
                  borderRadius: 8,
                  fontSize: 11,
                  fontFamily: 'JetBrains Mono',
                }}
                labelStyle={{ color: '#94a3b8' }}
                itemStyle={{ color: '#22d3ee' }}
              />
              <Line
                type="stepAfter"
                dataKey="score"
                stroke="#22d3ee"
                strokeWidth={2}
                dot={false}
                animationDuration={300}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
