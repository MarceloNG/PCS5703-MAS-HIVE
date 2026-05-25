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

      {view3D ? (
        <div className="flex-1 min-h-0 grid grid-cols-[1fr_340px]">
          {/* 3D viewport */}
          <div className="min-h-0">
            <GridScene3D />
          </div>

          {/* Side panel */}
          <div className="border-l border-border-dim flex flex-col min-h-0">
            <div className="flex-1 min-h-0 p-4 overflow-hidden border-b border-border-dim">
              <EventFeed />
            </div>
            <div className="h-[180px] p-4 overflow-hidden border-b border-border-dim">
              <BattleStats />
            </div>
            <div className="h-[150px] p-4 overflow-hidden">
              <ScoreTimeline />
            </div>
          </div>
        </div>
      ) : (
        <div className="flex-1 min-h-0 grid grid-rows-[auto_1fr_auto] gap-0">
          {/* Row 1: Agents overview */}
          <div className="p-4 border-b border-border-dim max-h-[240px] overflow-hidden">
            <AgentGrid />
          </div>

          {/* Row 2: Squads | Tasks | Event Feed */}
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

          {/* Row 3: Battle Stats | Auction Hall | Score Timeline */}
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
  )
}
