import { useRef, useMemo, useState } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { OrbitControls, Line } from '@react-three/drei'
import { useHiveStore, type MapMarker } from '../lib/store'
import type { AgentState } from '../lib/types'
import * as THREE from 'three'

const CELL = 0.6
const AGENT_Y = 0.3

const roleColors: Record<string, string> = {
  squad_leader: '#fbbf24',
  collector: '#22d3ee',
  assembler: '#a78bfa',
  sentinel: '#34d399',
}

const dispenserColors: Record<string, string> = {
  b0: '#ef4444',
  b1: '#3b82f6',
  b2: '#f59e0b',
  b3: '#8b5cf6',
  b4: '#ec4899',
}

function getAgentCentroid(): [number, number] | null {
  const agents = Object.values(useHiveStore.getState().agents)
  if (agents.length === 0) return null
  let cx = 0, cz = 0
  for (const ag of agents) {
    cx += ag.x * CELL
    cz += ag.y * CELL
  }
  return [cx / agents.length, cz / agents.length]
}

let recenterRequested = false
export function requestRecenter() { recenterRequested = true }

function CameraController() {
  const { camera } = useThree()
  const controlsRef = useRef<any>(null)
  const frameCount = useRef(0)

  useFrame(() => {
    frameCount.current++

    const shouldCenter = recenterRequested || frameCount.current === 30
    if (!shouldCenter) return

    const centroid = getAgentCentroid()
    if (!centroid) return

    const [cx, cz] = centroid
    recenterRequested = false

    camera.position.set(cx + 10, 14, cz + 10)
    camera.lookAt(cx, 0, cz)
    camera.updateProjectionMatrix()

    if (controlsRef.current) {
      controlsRef.current.target.set(cx, 0, cz)
      controlsRef.current.update()
    }
  })

  return (
    <OrbitControls
      ref={controlsRef}
      makeDefault
      enableDamping
      dampingFactor={0.08}
      minDistance={2}
      maxDistance={100}
      maxPolarAngle={Math.PI / 2.1}
      target={[0, 0, 0]}
    />
  )
}

function AgentLabel({ agent }: { agent: AgentState }) {
  const canvasTexture = useMemo(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 128
    canvas.height = 32
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = 'transparent'
    ctx.clearRect(0, 0, 128, 32)
    ctx.font = '20px monospace'
    ctx.textAlign = 'center'
    ctx.fillStyle = '#94a3b8'
    ctx.fillText(agent.name.replace('connectionA', 'A'), 64, 22)
    const tex = new THREE.CanvasTexture(canvas)
    tex.needsUpdate = true
    return tex
  }, [agent.name])

  return (
    <sprite position={[agent.x * CELL, AGENT_Y + 0.55, agent.y * CELL]} scale={[0.8, 0.2, 1]}>
      <spriteMaterial map={canvasTexture} transparent opacity={0.9} />
    </sprite>
  )
}

function AgentMesh({ agent }: { agent: AgentState }) {
  const meshRef = useRef<THREE.Mesh>(null!)
  const target = useMemo(() => new THREE.Vector3(), [])

  const color = new THREE.Color(roleColors[agent.role] ?? '#888888')

  useFrame(() => {
    if (!meshRef.current) return
    target.set(agent.x * CELL, AGENT_Y, agent.y * CELL)
    meshRef.current.position.lerp(target, 0.12)

    if (!agent.active) {
      meshRef.current.rotation.z = Math.PI / 4
      meshRef.current.position.y = 0.1
    } else {
      meshRef.current.rotation.z = 0
      meshRef.current.position.y = AGENT_Y + Math.sin(Date.now() * 0.003) * 0.02
    }
  })

  const scale = agent.role === 'squad_leader' ? 0.3 : agent.role === 'sentinel' ? 0.27 : 0.24

  return (
    <group>
      <mesh
        ref={meshRef}
        position={[agent.x * CELL, AGENT_Y, agent.y * CELL]}
      >
        {agent.role === 'squad_leader' ? (
          <octahedronGeometry args={[scale, 0]} />
        ) : agent.role === 'sentinel' ? (
          <dodecahedronGeometry args={[scale, 0]} />
        ) : agent.role === 'assembler' ? (
          <tetrahedronGeometry args={[scale, 0]} />
        ) : (
          <boxGeometry args={[scale * 1.5, scale * 1.5, scale * 1.5]} />
        )}
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={agent.active ? 0.6 : 0.05}
          transparent
          opacity={agent.active ? 1 : 0.25}
          roughness={0.3}
          metalness={0.6}
        />
      </mesh>

      <AgentLabel agent={agent} />

      {/* Energy bar background */}
      {agent.active && agent.energy >= 0 && (
        <>
          <mesh position={[agent.x * CELL, AGENT_Y + 0.42, agent.y * CELL]}>
            <boxGeometry args={[0.4, 0.05, 0.02]} />
            <meshBasicMaterial color="#1e293b" />
          </mesh>
          <mesh position={[agent.x * CELL + (agent.energy / 100 - 1) * 0.2, AGENT_Y + 0.42, agent.y * CELL + 0.005]}>
            <boxGeometry args={[0.4 * Math.max(0.01, agent.energy / 100), 0.05, 0.02]} />
            <meshBasicMaterial color={agent.energy > 30 ? '#34d399' : agent.energy > 10 ? '#fbbf24' : '#ef4444'} />
          </mesh>
        </>
      )}

      {agent.destX != null && agent.destY != null && agent.active && (
        <DestinationLine
          fromX={agent.x} fromY={agent.y}
          toX={agent.destX} toY={agent.destY}
          color={roleColors[agent.role] ?? '#888888'}
        />
      )}
    </group>
  )
}

function DestinationLine({ fromX, fromY, toX, toY, color }: {
  fromX: number; fromY: number; toX: number; toY: number; color: string
}) {
  const points = useMemo(() => [
    new THREE.Vector3(fromX * CELL, 0.05, fromY * CELL),
    new THREE.Vector3(toX * CELL, 0.05, toY * CELL),
  ], [fromX, fromY, toX, toY])

  return (
    <Line
      points={points}
      color={color}
      lineWidth={1.5}
      dashed
      dashSize={0.2}
      gapSize={0.12}
      opacity={0.4}
      transparent
    />
  )
}

function DispenserMesh({ marker }: { marker: MapMarker }) {
  const meshRef = useRef<THREE.Mesh>(null!)
  const color = new THREE.Color(dispenserColors[marker.type] ?? '#888888')

  useFrame(() => {
    if (meshRef.current) {
      meshRef.current.rotation.y += 0.01
    }
  })

  return (
    <group position={[marker.x * CELL, 0, marker.y * CELL]}>
      <mesh ref={meshRef} position={[0, 0.3, 0]}>
        <boxGeometry args={[0.25, 0.6, 0.25]} />
        <meshStandardMaterial
          color={color}
          emissive={color}
          emissiveIntensity={0.5}
          roughness={0.4}
          metalness={0.5}
        />
      </mesh>
      {/* Glow ring at base */}
      <mesh position={[0, 0.02, 0]} rotation={[-Math.PI / 2, 0, 0]}>
        <ringGeometry args={[0.15, 0.25, 16]} />
        <meshBasicMaterial color={color} transparent opacity={0.3} side={THREE.DoubleSide} />
      </mesh>
    </group>
  )
}

function GoalZoneMesh({ x, y }: { x: number; y: number }) {
  const meshRef = useRef<THREE.Mesh>(null!)

  useFrame(() => {
    if (meshRef.current) {
      const mat = meshRef.current.material as THREE.MeshStandardMaterial
      mat.opacity = 0.2 + Math.sin(Date.now() * 0.002) * 0.1
    }
  })

  return (
    <mesh ref={meshRef} position={[x * CELL, 0.02, y * CELL]} rotation={[-Math.PI / 2, 0, 0]}>
      <planeGeometry args={[CELL * 1.5, CELL * 1.5]} />
      <meshStandardMaterial
        color="#34d399"
        emissive="#34d399"
        emissiveIntensity={0.4}
        transparent
        opacity={0.25}
        side={THREE.DoubleSide}
      />
    </mesh>
  )
}

function GridFloor() {
  return (
    <group>
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
        <planeGeometry args={[200, 200]} />
        <meshStandardMaterial color="#080810" />
      </mesh>
      <gridHelper args={[200, 200, '#1a2332', '#0f1520']} />
    </group>
  )
}

function Scene() {
  const agents = useHiveStore(s => Object.values(s.agents))
  const dispensers = useHiveStore(s => s.dispensers)
  const goalZones = useHiveStore(s => s.goalZones)

  return (
    <>
      <ambientLight intensity={0.5} />
      <directionalLight position={[20, 30, 20]} intensity={1.2} color="#e2e8f0" />
      <pointLight position={[0, 20, 0]} intensity={0.5} color="#22d3ee" distance={200} />
      <hemisphereLight args={['#1a2332', '#080810', 0.3]} />

      <GridFloor />

      {goalZones.map((gz, i) => (
        <GoalZoneMesh key={`gz-${i}`} x={gz.x} y={gz.y} />
      ))}

      {dispensers.map((d, i) => (
        <DispenserMesh key={`disp-${i}`} marker={d} />
      ))}

      {agents.map(ag => (
        <AgentMesh key={ag.name} agent={ag} />
      ))}

      <CameraController />
    </>
  )
}

export function GridScene3D() {
  const connected = useHiveStore(s => s.connected)
  const agents = useHiveStore(s => s.agents)
  const dispensers = useHiveStore(s => s.dispensers)
  const goalZones = useHiveStore(s => s.goalZones)
  const agentList = Object.values(agents)
  const agentCount = agentList.length

  const [debugOpen, setDebugOpen] = useState(false)

  return (
    <div className="w-full h-full relative">
      <Canvas
        camera={{ position: [15, 20, 15], fov: 50, near: 0.1, far: 500 }}
        gl={{ antialias: true, alpha: false }}
        style={{ background: '#080810' }}
      >
        <Scene />
      </Canvas>

      {/* HUD */}
      <div className="absolute top-3 left-3 flex gap-2">
        <div className="px-2 py-1 rounded bg-surface-card/80 border border-border-dim text-[10px] font-mono text-slate-400 backdrop-blur-sm">
          3D VIEW
        </div>
        <div className="px-2 py-1 rounded bg-surface-card/80 border border-border-dim text-[10px] font-mono text-neon-cyan backdrop-blur-sm">
          {agentCount} agentes · {dispensers.length} disp · {goalZones.length} goals
        </div>
        {!connected && (
          <div className="px-2 py-1 rounded bg-red-500/10 border border-red-500/30 text-[10px] font-mono text-red-400 backdrop-blur-sm">
            OFFLINE
          </div>
        )}
        <button
          onClick={() => { requestRecenter() }}
          className="px-2 py-1 rounded bg-neon-cyan/10 border border-neon-cyan/30 text-[10px] font-mono text-neon-cyan hover:bg-neon-cyan/20 backdrop-blur-sm"
        >
          RECENTRALIZAR
        </button>
        <button
          onClick={() => setDebugOpen(v => !v)}
          className="px-2 py-1 rounded bg-surface-card/80 border border-border-dim text-[10px] font-mono text-slate-500 hover:text-slate-300 backdrop-blur-sm"
        >
          DEBUG
        </button>
      </div>

      {/* Debug panel */}
      {debugOpen && (
        <div className="absolute top-10 left-3 w-64 max-h-[320px] overflow-y-auto rounded bg-surface-card/95 border border-border-dim p-2 text-[9px] font-mono text-slate-400 backdrop-blur-sm space-y-0.5">
          {agentList.length === 0 ? (
            <div className="text-slate-600">Nenhum agente no store</div>
          ) : (
            agentList.map(ag => (
              <div key={ag.name} className="flex justify-between">
                <span className="text-slate-300">{ag.name.replace('connectionA', 'A')}</span>
                <span>({ag.x},{ag.y}) → 3D({(ag.x * CELL).toFixed(1)},{(ag.y * CELL).toFixed(1)})</span>
                <span className={ag.active ? 'text-emerald-400' : 'text-red-400'}>
                  {ag.role.replace('squad_', '').slice(0, 4)}
                </span>
              </div>
            ))
          )}
          <div className="border-t border-slate-800 pt-1 mt-1 text-[8px]">
            <div>dispensers: {dispensers.map(d => `(${d.x},${d.y}:${d.type})`).join(' ')}</div>
            <div>goals: {goalZones.map(g => `(${g.x},${g.y})`).join(' ')}</div>
          </div>
        </div>
      )}

      {/* Legend */}
      <div className="absolute bottom-3 left-3 flex gap-3 px-2 py-1.5 rounded bg-surface-card/80 border border-border-dim backdrop-blur-sm">
        {Object.entries(roleColors).map(([role, color]) => (
          <div key={role} className="flex items-center gap-1 text-[9px] font-mono text-slate-400">
            <div className="w-2 h-2 rounded-sm" style={{ backgroundColor: color }} />
            {role.replace('squad_', '')}
          </div>
        ))}
        <div className="w-px bg-slate-700" />
        <div className="flex items-center gap-1 text-[9px] font-mono text-slate-400">
          <div className="w-2 h-2 rounded-sm bg-emerald-400/50" />
          goal
        </div>
        <div className="flex items-center gap-1 text-[9px] font-mono text-slate-400">
          <div className="w-2 h-2 rounded-sm bg-red-400" />
          disp
        </div>
      </div>

      <div className="absolute bottom-3 right-3 px-2 py-1 rounded bg-surface-card/80 border border-border-dim text-[9px] font-mono text-slate-600 backdrop-blur-sm">
        LMB: orbitar · RMB: pan · Scroll: zoom
      </div>
    </div>
  )
}
