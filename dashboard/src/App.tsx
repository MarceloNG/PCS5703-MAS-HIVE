import { useState } from 'react'
import { useHiveSocket } from './lib/ws'
import { Header } from './components/Header'
import { SquadsPanel } from './components/SquadsPanel'
import { TaskPipeline } from './components/TaskPipeline'
import { EventFeed } from './components/EventFeed'
import { AuctionHall } from './components/AuctionHall'
import { AgentGrid } from './components/AgentGrid'
import { ScoreTimeline } from './components/ScoreTimeline'
import { BattleStats } from './components/BattleStats'
import { GridScene3D } from './components/GridScene3D'

export default function App() {
  useHiveSocket()
  const [view3D, setView3D] = useState(false)

  return (
    <div className="h-screen w-screen flex flex-col bg-surface grid-bg">
      <Header onToggle3D={() => setView3D(v => !v)} is3D={view3D} />

      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        {/* 3D — always mounted, hidden when 2D to preserve WebGL context */}
        <div style={{
          display: view3D ? 'flex' : 'none',
          height: '100%', overflow: 'hidden',
        }}>
          <div style={{ flex: 1 }}>
            <GridScene3D visible={view3D} />
          </div>
          <div style={{ width: 340, borderLeft: '1px solid rgba(34,211,238,0.15)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            <div style={{ flex: 1, minHeight: 0, padding: 16, overflow: 'hidden', borderBottom: '1px solid rgba(34,211,238,0.15)' }}>
              <EventFeed />
            </div>
            <div style={{ height: 180, padding: 16, overflow: 'hidden', borderBottom: '1px solid rgba(34,211,238,0.15)' }}>
              <BattleStats />
            </div>
            <div style={{ height: 150, padding: 16, overflow: 'hidden' }}>
              <ScoreTimeline />
            </div>
          </div>
        </div>

        {/* 2D */}
        {!view3D && (
          <div className="h-full grid grid-rows-[auto_1fr_auto] gap-0">
            <div className="p-4 border-b border-border-dim max-h-[240px] overflow-hidden">
              <AgentGrid />
            </div>
            <div className="grid grid-cols-[220px_1fr_340px] gap-px border-b border-border-dim min-h-0">
              <div className="p-4 border-r border-border-dim overflow-hidden">
                <SquadsPanel />
              </div>
              <div className="p-4 border-r border-border-dim overflow-hidden">
                <TaskPipeline />
              </div>
              <div className="p-4 overflow-hidden">
                <EventFeed />
              </div>
            </div>
            <div className="grid grid-cols-[320px_1fr_400px] gap-px border-b border-border-dim h-[210px]">
              <div className="p-4 border-r border-border-dim overflow-hidden">
                <BattleStats />
              </div>
              <div className="p-4 border-r border-border-dim overflow-hidden">
                <AuctionHall />
              </div>
              <div className="p-4 overflow-hidden">
                <ScoreTimeline />
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
