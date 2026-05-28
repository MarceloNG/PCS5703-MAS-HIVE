import { create } from 'zustand'
import type { Squad, TaskInfo, Auction, HiveEvent, ScorePoint, HiveSnapshot, HiveEventMessage, TaskPhase, AgentState, SnapshotAgent } from './types'

export interface BattleStats {
  deactivations: number
  reactivations: number
  clearWarnings: number
  lowEnergy: number
  submitsOk: number
  submitsFail: number
  connectsOk: number
  connectsFail: number
  blocksCollected: number
  tasksFinalized: number
  auctionsWon: number
  auctionsLost: number
}

export interface MapMarker {
  x: number
  y: number
  type: string
}

interface HiveState {
  connected: boolean
  step: number
  score: number
  squads: Squad[]
  tasks: TaskInfo[]
  auctions: Auction[]
  events: HiveEvent[]
  scoreHistory: ScorePoint[]
  agents: Record<string, AgentState>
  battleStats: BattleStats
  dispensers: MapMarker[]
  goalZones: MapMarker[]
  setConnected: (c: boolean) => void
  applySnapshot: (s: HiveSnapshot) => void
  applyEvent: (e: HiveEventMessage) => void
}

let eventCounter = 0

export const useHiveStore = create<HiveState>((set, get) => ({
  connected: false,
  step: 0,
  score: 0,
  squads: [],
  tasks: [],
  auctions: [],
  events: [],
  scoreHistory: [],
  agents: {},
  dispensers: [],
  goalZones: [],
  battleStats: {
    deactivations: 0, reactivations: 0, clearWarnings: 0, lowEnergy: 0,
    submitsOk: 0, submitsFail: 0, connectsOk: 0, connectsFail: 0,
    blocksCollected: 0, tasksFinalized: 0, auctionsWon: 0, auctionsLost: 0,
  },

  setConnected: (c) => set({ connected: c }),

  applySnapshot: (s) => {
    const eventsWithIds = s.events.slice(-200).map((ev, i) => ({
      ...ev,
      id: ev.id || `snap-${++eventCounter}-${i}`,
    }))
    const agents: Record<string, AgentState> = {}
    if (s.agents) {
      for (const a of s.agents) {
        agents[a.name] = {
          name: a.name,
          role: a.role ?? 'unknown',
          x: a.x ?? 0,
          y: a.y ?? 0,
          energy: a.energy ?? -1,
          action: a.action ?? 'none',
          result: a.result ?? 'none',
          active: a.active !== 'false' && a.active !== false,
          destX: a.destX,
          destY: a.destY,
          lastUpdate: a.lastUpdate ?? s.step,
        }
      }
    }
    const battleStats = buildBattleStatsFromEvents(eventsWithIds)
    const dispensers = (s.dispensers ?? []).map(d => ({ x: d.x, y: d.y, type: d.type ?? 'b0' }))
    const goalZones = (s.goalZones ?? []).map(g => ({ x: g.x, y: g.y, type: 'goal' }))
    set({
      step: s.step,
      score: s.score,
      squads: s.squads,
      tasks: s.tasks,
      auctions: s.auctions,
      events: eventsWithIds,
      scoreHistory: s.scoreHistory,
      agents,
      battleStats,
      dispensers,
      goalZones,
    })
  },

  applyEvent: (e) => {
    const state = get()
    const newEvent: HiveEvent = {
      id: `evt-${++eventCounter}`,
      ts: e.ts,
      step: e.step,
      event: e.event,
      agent: e.agent,
      data: e.data,
    }
    const events = [...state.events, newEvent].slice(-200)
    const step = e.step > state.step ? e.step : state.step

    let { score, scoreHistory, squads, tasks, auctions } = state

    switch (e.event) {
      case 'score_update':
        score = (e.data.score as number) ?? score
        scoreHistory = [...scoreHistory, { step, score }]
        break

      case 'step_update':
        break

      case 'bid_placed': {
        const taskName = e.data.task as string
        const existing = auctions.find(a => a.task === taskName)
        const bid = { squad: e.data.squad as string, value: e.data.value as number, winner: false }
        if (existing) {
          auctions = auctions.map(a =>
            a.task === taskName ? { ...a, bids: [...a.bids, bid] } : a
          )
        } else {
          auctions = [...auctions, { task: taskName, bids: [bid], resolved: false }]
        }
        break
      }

      case 'auction_won': {
        const tn = e.data.task as string
        const winSquad = e.data.squad as string
        auctions = auctions.map(a =>
          a.task === tn
            ? { ...a, resolved: true, bids: a.bids.map(b => ({ ...b, winner: b.squad === winSquad })) }
            : a
        )
        tasks = upsertTask(tasks, tn, { phase: 'collect', squad: winSquad })
        break
      }

      case 'task_phase_update': {
        const tn2 = e.data.task as string
        tasks = upsertTask(tasks, tn2, {
          phase: e.data.phase as TaskPhase,
          progress: (e.data.progress as number) ?? 0,
        })
        break
      }

      case 'squad_update': {
        const sid = e.data.squad as string
        const members = e.data.members as Squad['members'] | undefined
        if (members) {
          const idx = squads.findIndex(s => s.id === sid)
          if (idx >= 0) {
            squads = squads.map(s => s.id === sid ? { ...s, members } : s)
          } else {
            squads = [...squads, { id: sid, members }]
          }
          const seen = new Set<string>()
          squads = squads.filter(s => seen.has(s.id) ? false : (seen.add(s.id), true))
        }
        break
      }

      case 'task_finalized': {
        const tn3 = e.data.task as string
        tasks = tasks.filter(t => t.name !== tn3)
        auctions = auctions.filter(a => a.task !== tn3)
        break
      }

      case 'agent_state': {
        const agName = e.agent
        const agents = { ...state.agents }
        agents[agName] = {
          name: agName,
          role: (e.data.role as string) ?? 'unknown',
          x: (e.data.x as number) ?? 0,
          y: (e.data.y as number) ?? 0,
          energy: (e.data.energy as number) ?? -1,
          action: (e.data.action as string) ?? 'none',
          result: (e.data.result as string) ?? 'none',
          active: e.data.active !== 'false' && e.data.active !== false,
          destX: e.data.destX as number | undefined,
          destY: e.data.destY as number | undefined,
          lastUpdate: step,
        }
        set({ agents })
        break
      }
    }

    let { dispensers, goalZones } = state
    if (e.event === 'map_dispenser') {
      const dx = e.data.x as number, dy = e.data.y as number, dt = (e.data.type as string) ?? 'b0'
      if (!dispensers.some(d => d.x === dx && d.y === dy && d.type === dt)) {
        dispensers = [...dispensers, { x: dx, y: dy, type: dt }]
      }
    }
    if (e.event === 'map_goal_zone') {
      const gx = e.data.x as number, gy = e.data.y as number
      if (!goalZones.some(g => g.x === gx && g.y === gy)) {
        goalZones = [...goalZones, { x: gx, y: gy, type: 'goal' }]
      }
    }

    const bs = { ...state.battleStats }
    switch (e.event) {
      case 'deactivated': bs.deactivations++; break
      case 'reactivated': bs.reactivations++; break
      case 'clear_warning': bs.clearWarnings++; break
      case 'low_energy': bs.lowEnergy++; break
      case 'submit_success': bs.submitsOk++; break
      case 'submit_fail': bs.submitsFail++; break
      case 'connect_success': bs.connectsOk++; break
      case 'connect_fail': bs.connectsFail++; break
      case 'block_collected': bs.blocksCollected++; break
      case 'task_finalized': bs.tasksFinalized++; break
      case 'auction_won': bs.auctionsWon++; break
      case 'auction_lost': bs.auctionsLost++; break
    }

    set({ events, step, score, scoreHistory, squads, tasks, auctions, battleStats: bs, dispensers, goalZones })
  },
}))

function buildBattleStatsFromEvents(events: HiveEvent[]): BattleStats {
  const bs: BattleStats = {
    deactivations: 0, reactivations: 0, clearWarnings: 0, lowEnergy: 0,
    submitsOk: 0, submitsFail: 0, connectsOk: 0, connectsFail: 0,
    blocksCollected: 0, tasksFinalized: 0, auctionsWon: 0, auctionsLost: 0,
  }
  for (const ev of events) {
    switch (ev.event) {
      case 'deactivated': bs.deactivations++; break
      case 'reactivated': bs.reactivations++; break
      case 'clear_warning': bs.clearWarnings++; break
      case 'low_energy': bs.lowEnergy++; break
      case 'submit_success': bs.submitsOk++; break
      case 'submit_fail': bs.submitsFail++; break
      case 'connect_success': bs.connectsOk++; break
      case 'connect_fail': bs.connectsFail++; break
      case 'block_collected': bs.blocksCollected++; break
      case 'task_finalized': bs.tasksFinalized++; break
      case 'auction_won': bs.auctionsWon++; break
      case 'auction_lost': bs.auctionsLost++; break
    }
  }
  return bs
}

function upsertTask(tasks: TaskInfo[], name: string, patch: Partial<TaskInfo>): TaskInfo[] {
  const exists = tasks.find(t => t.name === name)
  if (exists) {
    return tasks.map(t => t.name === name ? { ...t, ...patch } : t)
  }
  return [...tasks, { name, phase: 'auction', progress: 0, ...patch }]
}
