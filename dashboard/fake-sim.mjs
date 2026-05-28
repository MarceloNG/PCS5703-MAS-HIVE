import { WebSocketServer } from 'ws';

const PORT = 8765;
const TICK_MS = 800;
const GRID = 40;
const AGENT_NAMES = Array.from({ length: 15 }, (_, i) => `connectionA${i + 1}`);
const ROLES = [
  'squad_leader', 'squad_leader', 'squad_leader',
  'collector', 'collector', 'collector', 'collector', 'collector', 'collector',
  'assembler', 'assembler', 'assembler',
  'sentinel', 'sentinel', 'sentinel',
];
const SQUAD_ROLES = ['leader', 'collector', 'collector', 'assembler'];
const BLOCK_TYPES = ['b0', 'b1', 'b2', 'b3'];
const ACTIONS = ['move', 'move', 'move', 'move', 'request', 'attach', 'skip'];
const RESULTS = ['success', 'success', 'success', 'success', 'failed_path', 'failed_target'];
const TASK_PHASES = ['auction', 'collect', 'meet', 'connect', 'submit', 'done'];
const EVENT_NAMES = [
  'block_collected', 'submit_success', 'submit_fail',
  'connect_success', 'connect_fail', 'task_delegated',
  'collect_started', 'arrived_dest', 'low_energy',
];

const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const pick = (arr) => arr[rand(0, arr.length - 1)];
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const wrap = (v) => ((v % GRID) + GRID) % GRID;

const agents = AGENT_NAMES.map((name, i) => ({
  name,
  role: ROLES[i],
  x: rand(0, GRID - 1),
  y: rand(0, GRID - 1),
  energy: rand(60, 100),
  action: 'move',
  result: 'success',
  active: true,
  destX: rand(0, GRID - 1),
  destY: rand(0, GRID - 1),
  lastUpdate: 0,
}));

const dispensers = Array.from({ length: 12 }, () => ({
  x: rand(0, GRID - 1),
  y: rand(0, GRID - 1),
  type: pick(BLOCK_TYPES),
}));

const goalZones = [];
for (let i = 0; i < 3; i++) {
  const cx = rand(5, GRID - 5);
  const cy = rand(5, GRID - 5);
  for (let dx = -1; dx <= 1; dx++)
    for (let dy = -1; dy <= 1; dy++)
      goalZones.push({ x: wrap(cx + dx), y: wrap(cy + dy) });
}

let step = 0;
let score = 0;
let taskCounter = 0;
const scoreHistory = [{ step: 0, score: 0 }];
const tasks = [];
const auctions = [];
const squads = [
  { id: 'squad_alpha', members: [], task: null, meetingPoint: null },
  { id: 'squad_beta', members: [], task: null, meetingPoint: null },
  { id: 'squad_gamma', members: [], task: null, meetingPoint: null },
];

squads.forEach((sq, si) => {
  const base = si * 4;
  sq.members = SQUAD_ROLES.map((role, j) => ({
    name: AGENT_NAMES[base + j],
    role,
    block: j === 1 ? pick(BLOCK_TYPES) : undefined,
    status: 'active',
  }));
});

function buildSnapshot() {
  return {
    type: 'snapshot',
    step,
    score,
    squads,
    tasks,
    auctions,
    events: [],
    scoreHistory,
    agents,
    dispensers,
    goalZones,
  };
}

function moveAgents() {
  for (const a of agents) {
    const dx = a.destX - a.x;
    const dy = a.destY - a.y;
    if (Math.abs(dx) + Math.abs(dy) < 2) {
      a.destX = rand(0, GRID - 1);
      a.destY = rand(0, GRID - 1);
    }
    a.x = wrap(a.x + (dx > 0 ? 1 : dx < 0 ? -1 : 0));
    a.y = wrap(a.y + (dy > 0 ? 1 : dy < 0 ? -1 : 0));
    a.energy = clamp(a.energy + rand(-3, 2), 10, 100);
    a.action = pick(ACTIONS);
    a.result = pick(RESULTS);
    a.lastUpdate = step;
  }
}

function makeEvent(event, agent, data) {
  return {
    type: 'event',
    ts: Date.now(),
    step,
    event,
    agent,
    data,
  };
}

function tick(broadcast) {
  step++;
  moveAgents();

  broadcast(JSON.stringify({ type: 'step_update', step, score }));

  for (const a of agents) {
    broadcast(JSON.stringify(makeEvent('agent_state', a.name, {
      x: a.x, y: a.y, role: a.role, energy: a.energy,
      action: a.action, result: a.result, active: true,
      destX: a.destX, destY: a.destY,
    })));
  }

  if (step % 8 === 0) {
    score += 10;
    scoreHistory.push({ step, score });
    broadcast(JSON.stringify(makeEvent('score_update', 'system', { score })));
    broadcast(JSON.stringify(makeEvent('submit_success', pick(AGENT_NAMES), {
      task: `task_${String(taskCounter).padStart(3, '0')}`,
      block: pick(BLOCK_TYPES),
    })));
  }

  if (step % 12 === 0) {
    taskCounter++;
    const tname = `task_${String(taskCounter).padStart(3, '0')}`;
    const reward = pick([10, 20, 30, 50]);
    tasks.push({
      name: tname,
      phase: 'auction',
      progress: 0,
      squad: null,
      reward,
      deadline: step + 200,
    });
    auctions.push({
      task: tname,
      resolved: false,
      bids: [],
    });
  }

  if (step % 14 === 0 && auctions.length > 0) {
    const auc = auctions.find(a => !a.resolved);
    if (auc) {
      const sq = pick(squads);
      const val = rand(5, 20);
      auc.bids.push({ squad: sq.id, value: val, winner: false });
      broadcast(JSON.stringify(makeEvent('bid_placed', pick(AGENT_NAMES), {
        task: auc.task, squad: sq.id, value: val,
      })));

      const sq2 = pick(squads.filter(s => s.id !== sq.id));
      const val2 = rand(5, 20);
      auc.bids.push({ squad: sq2.id, value: val2, winner: false });
      broadcast(JSON.stringify(makeEvent('bid_placed', pick(AGENT_NAMES), {
        task: auc.task, squad: sq2.id, value: val2,
      })));
    }
  }

  if (step % 16 === 0 && auctions.length > 0) {
    const auc = auctions.find(a => !a.resolved && a.bids.length >= 2);
    if (auc) {
      auc.resolved = true;
      const winBid = auc.bids.reduce((a, b) => a.value > b.value ? a : b);
      winBid.winner = true;
      const task = tasks.find(t => t.name === auc.task);
      if (task) {
        task.phase = 'collect';
        task.squad = winBid.squad;
        task.progress = 10;
      }
      broadcast(JSON.stringify(makeEvent('auction_won', 'system', {
        task: auc.task, squad: winBid.squad,
      })));
    }
  }

  if (step % 10 === 0) {
    for (const t of tasks) {
      if (t.phase !== 'done') {
        const pi = TASK_PHASES.indexOf(t.phase);
        if (t.progress >= 90 && pi < TASK_PHASES.length - 1) {
          t.phase = TASK_PHASES[pi + 1];
          t.progress = 10;
        } else {
          t.progress = Math.min(100, t.progress + rand(15, 35));
        }
        broadcast(JSON.stringify(makeEvent('task_phase_update', 'system', {
          task: t.name, phase: t.phase, progress: t.progress,
        })));
      }
    }
  }

  if (step % 20 === 0) {
    const sq = pick(squads);
    sq.task = tasks.find(t => t.squad === sq.id && t.phase !== 'done')?.name || null;
    sq.meetingPoint = { x: rand(0, GRID - 1), y: rand(0, GRID - 1) };
    for (const m of sq.members) {
      m.block = Math.random() > 0.6 ? pick(BLOCK_TYPES) : undefined;
    }
    broadcast(JSON.stringify(makeEvent('squad_update', 'system', {
      squad: sq.id, members: sq.members,
    })));
  }

  if (step % 6 === 0) {
    const evt = pick(EVENT_NAMES);
    const ag = pick(AGENT_NAMES);
    broadcast(JSON.stringify(makeEvent(evt, ag, {
      task: `task_${String(rand(1, taskCounter || 1)).padStart(3, '0')}`,
      block: pick(BLOCK_TYPES),
      result: pick(RESULTS),
    })));
  }

  if (step % 25 === 0) {
    broadcast(JSON.stringify(makeEvent('block_collected', pick(AGENT_NAMES), {
      task: `task_${String(rand(1, taskCounter || 1)).padStart(3, '0')}`,
      block: pick(BLOCK_TYPES),
    })));
  }

  if (step % 50 === 0 && Math.random() > 0.7) {
    const ag = pick(AGENT_NAMES);
    broadcast(JSON.stringify(makeEvent('deactivated', ag, {})));
    setTimeout(() => {
      broadcast(JSON.stringify(makeEvent('reactivated', ag, {})));
    }, 3000);
  }

  if (step % 30 === 0) {
    broadcast(JSON.stringify(makeEvent('map_dispenser', 'system', {
      x: rand(0, GRID - 1), y: rand(0, GRID - 1), type: pick(BLOCK_TYPES),
    })));
  }

  if (step % 40 === 0) {
    broadcast(JSON.stringify(makeEvent('map_goal_zone', 'system', {
      x: rand(0, GRID - 1), y: rand(0, GRID - 1),
    })));
  }

  if (tasks.length > 8) {
    const done = tasks.filter(t => t.phase === 'done');
    for (const d of done.slice(0, done.length - 3)) {
      const idx = tasks.indexOf(d);
      if (idx >= 0) {
        tasks.splice(idx, 1);
        const ai = auctions.findIndex(a => a.task === d.name);
        if (ai >= 0) auctions.splice(ai, 1);
        broadcast(JSON.stringify(makeEvent('task_finalized', 'system', { task: d.name })));
      }
    }
  }
}

const wss = new WebSocketServer({ port: PORT });

console.log(`\n  🐝 HIVE Fake Simulator running on ws://localhost:${PORT}`);
console.log(`  📊 Open http://localhost:5173/ to see the dashboard\n`);
console.log(`  Sending ${TICK_MS}ms ticks with 15 agents, dispensers, goal zones...`);
console.log(`  Press Ctrl+C to stop\n`);

const clients = new Set();

wss.on('connection', (ws) => {
  console.log(`  ✓ Dashboard connected`);
  clients.add(ws);
  ws.send(JSON.stringify(buildSnapshot()));
  ws.on('close', () => {
    clients.delete(ws);
    console.log(`  ✗ Dashboard disconnected`);
  });
});

setInterval(() => {
  if (clients.size === 0) return;
  const broadcast = (msg) => {
    for (const c of clients) {
      if (c.readyState === 1) c.send(msg);
    }
  };
  tick(broadcast);
  if (step % 20 === 0) {
    console.log(`  step=${step}  score=${score}  agents=15  tasks=${tasks.length}`);
  }
}, TICK_MS);
