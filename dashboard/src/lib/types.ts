export type TaskPhase = 'auction' | 'collect' | 'meet' | 'connect' | 'submit' | 'done'

export interface SquadMember {
  name: string
  role: 'leader' | 'collector' | 'assembler' | 'sentinel'
  block?: string
  status?: string
}

export interface Squad {
  id: string
  members: SquadMember[]
  task?: string
  meetingPoint?: { x: number; y: number }
}

export interface TaskInfo {
  name: string
  phase: TaskPhase
  progress: number
  squad?: string
  deadline?: number
  reward?: number
  nBlocks?: number
}

export interface AuctionBid {
  squad: string
  value: number
  winner: boolean
}

export interface Auction {
  task: string
  bids: AuctionBid[]
  resolved: boolean
}

export interface HiveEvent {
  id: string
  ts: number
  step: number
  event: string
  agent: string
  data: Record<string, unknown>
}

export interface AgentBelief {
  agent: string
  beliefs: string[]
}

export interface AgentState {
  name: string
  role: string
  x: number
  y: number
  energy: number
  action: string
  result: string
  active: boolean
  destX?: number
  destY?: number
  lastUpdate: number
}

export interface ScorePoint {
  step: number
  score: number
}

export interface SnapshotAgent {
  name: string
  role: string
  x: number
  y: number
  energy: number
  action: string
  result: string
  active: boolean | string
  destX?: number
  destY?: number
  lastUpdate: number
}

export interface HiveSnapshot {
  type: 'snapshot'
  step: number
  score: number
  squads: Squad[]
  tasks: TaskInfo[]
  auctions: Auction[]
  events: HiveEvent[]
  scoreHistory: ScorePoint[]
  agents?: SnapshotAgent[]
  dispensers?: { x: number; y: number; type: string }[]
  goalZones?: { x: number; y: number }[]
}

export interface HiveEventMessage {
  type: 'event'
  ts: number
  step: number
  event: string
  agent: string
  data: Record<string, unknown>
}

export type HiveMessage = HiveSnapshot | HiveEventMessage
