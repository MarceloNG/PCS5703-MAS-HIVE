import { useCallback, useEffect, useRef } from 'react'
import { create } from 'zustand'
import { useHiveStore } from './store'
import type { HiveSnapshot, HiveEventMessage, TaskPhase } from './types'

interface FakeSimState {
  active: boolean
  setActive: (v: boolean) => void
}

export const useFakeSimStore = create<FakeSimState>((set) => ({
  active: false,
  setActive: (v) => set({ active: v }),
}))

const GRID = 40
const TICK_MS = 800
const AGENT_NAMES = Array.from({ length: 15 }, (_, i) => `connectionA${i + 1}`)
const ROLES = [
  'squad_leader', 'squad_leader', 'squad_leader',
  'collector', 'collector', 'collector', 'collector', 'collector', 'collector',
  'assembler', 'assembler', 'assembler',
  'sentinel', 'sentinel', 'sentinel',
]
const SQUAD_ROLES: ('leader' | 'collector' | 'assembler' | 'sentinel')[] =
  ['leader', 'collector', 'collector', 'assembler']
const BLOCK_TYPES = ['b0', 'b1', 'b2', 'b3']
const ACTIONS = ['move', 'move', 'move', 'move', 'request', 'attach', 'skip']
const RESULTS = ['success', 'success', 'success', 'success', 'failed_path', 'failed_target']
const TASK_PHASES: TaskPhase[] = ['auction', 'collect', 'meet', 'connect', 'submit', 'done']
const GAMEPLAY_EVENTS = [
  'block_collected', 'submit_success', 'submit_fail',
  'connect_success', 'connect_fail', 'task_delegated',
  'collect_started', 'arrived_dest', 'low_energy',
]

const rand = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min
const pick = <T>(arr: T[]): T => arr[rand(0, arr.length - 1)]
const clamp = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v))
const wrap = (v: number) => ((v % GRID) + GRID) % GRID

interface SimAgent {
  name: string; role: string
  x: number; y: number
  energy: number; action: string; result: string
  active: boolean; destX: number; destY: number
}

interface SimTask {
  name: string; phase: TaskPhase; progress: number
  squad: string | null; reward: number; deadline: number
}

interface SimAuction {
  task: string; resolved: boolean
  bids: { squad: string; value: number; winner: boolean }[]
}

interface SimState {
  step: number; score: number; taskCounter: number
  agents: SimAgent[]
  dispensers: { x: number; y: number; type: string }[]
  goalZones: { x: number; y: number }[]
  scoreHistory: { step: number; score: number }[]
  tasks: SimTask[]
  auctions: SimAuction[]
  squads: { id: string; members: { name: string; role: 'leader' | 'collector' | 'assembler' | 'sentinel'; block?: string }[]; task: string | null; meetingPoint: { x: number; y: number } | null }[]
}

function createSimState(): SimState {
  const agents: SimAgent[] = AGENT_NAMES.map((name, i) => ({
    name, role: ROLES[i],
    x: rand(0, GRID - 1), y: rand(0, GRID - 1),
    energy: rand(60, 100), action: 'move', result: 'success',
    active: true, destX: rand(0, GRID - 1), destY: rand(0, GRID - 1),
  }))

  const dispensers = Array.from({ length: 12 }, () => ({
    x: rand(0, GRID - 1), y: rand(0, GRID - 1), type: pick(BLOCK_TYPES),
  }))

  const goalZones: { x: number; y: number }[] = []
  for (let i = 0; i < 3; i++) {
    const cx = rand(5, GRID - 5), cy = rand(5, GRID - 5)
    for (let dx = -1; dx <= 1; dx++)
      for (let dy = -1; dy <= 1; dy++)
        goalZones.push({ x: wrap(cx + dx), y: wrap(cy + dy) })
  }

  const squads = ['squad_alpha', 'squad_beta', 'squad_gamma'].map((id, si) => ({
    id,
    members: SQUAD_ROLES.map((role, j) => ({
      name: AGENT_NAMES[si * 4 + j], role,
      block: j === 1 ? pick(BLOCK_TYPES) : undefined,
    })),
    task: null as string | null,
    meetingPoint: null as { x: number; y: number } | null,
  }))

  return {
    step: 0, score: 0, taskCounter: 0,
    agents, dispensers, goalZones,
    scoreHistory: [{ step: 0, score: 0 }],
    tasks: [], auctions: [], squads,
  }
}

function buildSnapshot(s: SimState): HiveSnapshot {
  return {
    type: 'snapshot',
    step: s.step, score: s.score,
    squads: s.squads, tasks: s.tasks, auctions: s.auctions,
    events: [], scoreHistory: s.scoreHistory,
    agents: s.agents.map(a => ({ ...a, lastUpdate: s.step })),
    dispensers: s.dispensers, goalZones: s.goalZones,
  }
}

function makeEvent(s: SimState, event: string, agent: string, data: Record<string, unknown>): HiveEventMessage {
  return { type: 'event', ts: Date.now(), step: s.step, event, agent, data }
}

function simTick(s: SimState): HiveEventMessage[] {
  s.step++
  const events: HiveEventMessage[] = []

  for (const a of s.agents) {
    const dx = a.destX - a.x, dy = a.destY - a.y
    if (Math.abs(dx) + Math.abs(dy) < 2) {
      a.destX = rand(0, GRID - 1); a.destY = rand(0, GRID - 1)
    }
    a.x = wrap(a.x + (dx > 0 ? 1 : dx < 0 ? -1 : 0))
    a.y = wrap(a.y + (dy > 0 ? 1 : dy < 0 ? -1 : 0))
    a.energy = clamp(a.energy + rand(-3, 2), 10, 100)
    a.action = pick(ACTIONS); a.result = pick(RESULTS)

    events.push(makeEvent(s, 'agent_state', a.name, {
      x: a.x, y: a.y, role: a.role, energy: a.energy,
      action: a.action, result: a.result, active: true,
      destX: a.destX, destY: a.destY,
    }))
  }

  if (s.step % 8 === 0) {
    s.score += 10
    s.scoreHistory.push({ step: s.step, score: s.score })
    events.push(makeEvent(s, 'score_update', 'system', { score: s.score }))
    events.push(makeEvent(s, 'submit_success', pick(AGENT_NAMES), {
      task: `task_${String(s.taskCounter).padStart(3, '0')}`, block: pick(BLOCK_TYPES),
    }))
  }

  if (s.step % 12 === 0) {
    s.taskCounter++
    const tname = `task_${String(s.taskCounter).padStart(3, '0')}`
    const reward = pick([10, 20, 30, 50])
    s.tasks.push({ name: tname, phase: 'auction', progress: 0, squad: null, reward, deadline: s.step + 200 })
    s.auctions.push({ task: tname, resolved: false, bids: [] })
  }

  if (s.step % 14 === 0) {
    const auc = s.auctions.find(a => !a.resolved)
    if (auc) {
      const sq = pick(s.squads), val = rand(5, 20)
      auc.bids.push({ squad: sq.id, value: val, winner: false })
      events.push(makeEvent(s, 'bid_placed', pick(AGENT_NAMES), { task: auc.task, squad: sq.id, value: val }))
      const sq2 = pick(s.squads.filter(x => x.id !== sq.id)), val2 = rand(5, 20)
      auc.bids.push({ squad: sq2.id, value: val2, winner: false })
      events.push(makeEvent(s, 'bid_placed', pick(AGENT_NAMES), { task: auc.task, squad: sq2.id, value: val2 }))
    }
  }

  if (s.step % 16 === 0) {
    const auc = s.auctions.find(a => !a.resolved && a.bids.length >= 2)
    if (auc) {
      auc.resolved = true
      const winBid = auc.bids.reduce((a, b) => a.value > b.value ? a : b)
      winBid.winner = true
      const task = s.tasks.find(t => t.name === auc.task)
      if (task) { task.phase = 'collect'; task.squad = winBid.squad; task.progress = 10 }
      events.push(makeEvent(s, 'auction_won', 'system', { task: auc.task, squad: winBid.squad }))
    }
  }

  if (s.step % 10 === 0) {
    for (const t of s.tasks) {
      if (t.phase !== 'done') {
        const pi = TASK_PHASES.indexOf(t.phase)
        if (t.progress >= 90 && pi < TASK_PHASES.length - 1) {
          t.phase = TASK_PHASES[pi + 1]; t.progress = 10
        } else {
          t.progress = Math.min(100, t.progress + rand(15, 35))
        }
        events.push(makeEvent(s, 'task_phase_update', 'system', { task: t.name, phase: t.phase, progress: t.progress }))
      }
    }
  }

  if (s.step % 20 === 0) {
    const sq = pick(s.squads)
    sq.task = s.tasks.find(t => t.squad === sq.id && t.phase !== 'done')?.name || null
    sq.meetingPoint = { x: rand(0, GRID - 1), y: rand(0, GRID - 1) }
    for (const m of sq.members) m.block = Math.random() > 0.6 ? pick(BLOCK_TYPES) : undefined
    events.push(makeEvent(s, 'squad_update', 'system', { squad: sq.id, members: sq.members }))
  }

  if (s.step % 6 === 0) {
    events.push(makeEvent(s, pick(GAMEPLAY_EVENTS), pick(AGENT_NAMES), {
      task: `task_${String(rand(1, s.taskCounter || 1)).padStart(3, '0')}`,
      block: pick(BLOCK_TYPES), result: pick(RESULTS),
    }))
  }

  if (s.step % 50 === 0 && Math.random() > 0.7) {
    const ag = pick(AGENT_NAMES)
    events.push(makeEvent(s, 'deactivated', ag, {}))
  }

  if (s.step % 30 === 0) {
    events.push(makeEvent(s, 'map_dispenser', 'system', { x: rand(0, GRID - 1), y: rand(0, GRID - 1), type: pick(BLOCK_TYPES) }))
  }

  if (s.step % 40 === 0) {
    events.push(makeEvent(s, 'map_goal_zone', 'system', { x: rand(0, GRID - 1), y: rand(0, GRID - 1) }))
  }

  if (s.tasks.length > 8) {
    const done = s.tasks.filter(t => t.phase === 'done')
    for (const d of done.slice(0, done.length - 3)) {
      const idx = s.tasks.indexOf(d)
      if (idx >= 0) {
        s.tasks.splice(idx, 1)
        const ai = s.auctions.findIndex(a => a.task === d.name)
        if (ai >= 0) s.auctions.splice(ai, 1)
        events.push(makeEvent(s, 'task_finalized', 'system', { task: d.name }))
      }
    }
  }

  return events
}

export function useFakeSim() {
  const { active, setActive } = useFakeSimStore()
  const connected = useHiveStore(s => s.connected)
  const simRef = useRef<SimState | null>(null)
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const stop = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current)
      intervalRef.current = null
    }
    simRef.current = null
    setActive(false)
  }, [setActive])

  const start = useCallback(() => {
    stop()
    const sim = createSimState()
    simRef.current = sim

    const { applySnapshot, applyEvent } = useHiveStore.getState()
    applySnapshot(buildSnapshot(sim))
    useHiveStore.setState({ connected: false })

    intervalRef.current = setInterval(() => {
      if (!simRef.current) return
      const events = simTick(simRef.current)
      const s = simRef.current
      useHiveStore.setState({ step: s.step, score: s.score })
      for (const ev of events) applyEvent(ev)
    }, TICK_MS)

    setActive(true)
  }, [stop, setActive])

  const toggle = useCallback(() => {
    if (active) {
      stop()
      useHiveStore.setState({
        step: 0, score: 0, squads: [], tasks: [], auctions: [],
        events: [], scoreHistory: [], agents: {}, dispensers: [], goalZones: [],
        battleStats: {
          deactivations: 0, reactivations: 0, clearWarnings: 0, lowEnergy: 0,
          submitsOk: 0, submitsFail: 0, connectsOk: 0, connectsFail: 0,
          blocksCollected: 0, tasksFinalized: 0, auctionsWon: 0, auctionsLost: 0,
        },
      })
    } else {
      start()
    }
  }, [active, stop, start])

  useEffect(() => () => { stop() }, [stop])

  return { active, toggle, disabled: connected && !active }
}
