import { Component, useRef, useMemo, useState, type ReactNode, type ErrorInfo } from 'react'
import { Canvas, useFrame, useThree } from '@react-three/fiber'
import { OrbitControls } from '@react-three/drei'
import { useHiveStore, type MapMarker } from '../lib/store'
import type { AgentState } from '../lib/types'
import { useShallow } from 'zustand/shallow'
import * as THREE from 'three'

class Canvas3DErrorBoundary extends Component<{ children: ReactNode; onError: (msg: string) => void }, { hasError: boolean }> {
  state = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch(error: Error, _info: ErrorInfo) { this.props.onError(error.message) }
  render() { return this.state.hasError ? null : this.props.children }
}

const CELL = 0.6
const AGENT_Y = 0.35

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

function CameraRig() {
  const { camera } = useThree()
  const controlsRef = useRef<any>(null)
  const done = useRef(false)
  const tick = useRef(0)

  useFrame(() => {
    tick.current++
    if (done.current && tick.current % 300 !== 0) return

    const agents = Object.values(useHiveStore.getState().agents)
    if (agents.length === 0) return

    let cx = 0, cz = 0
    for (const ag of agents) { cx += ag.x; cz += ag.y }
    cx = (cx / agents.length) * CELL
    cz = (cz / agents.length) * CELL

    if (!done.current) {
      camera.position.set(cx + 10, 14, cz + 10)
      camera.lookAt(cx, 0, cz)
      if (controlsRef.current) {
        controlsRef.current.target.set(cx, 0, cz)
        controlsRef.current.update()
      }
      done.current = true
    }
  })

  return <OrbitControls ref={controlsRef} enableDamping dampingFactor={0.1} maxPolarAngle={Math.PI / 2.1} />
}

function AgentBox({ agent }: { agent: AgentState }) {
  const ref = useRef<THREE.Mesh>(null!)
  const color = roleColors[agent.role] ?? '#888'
  const sz = agent.role === 'squad_leader' ? 0.32 : 0.24

  useFrame(() => {
    if (!ref.current) return
    const tx = agent.x * CELL, tz = agent.y * CELL
    ref.current.position.x += (tx - ref.current.position.x) * 0.1
    ref.current.position.z += (tz - ref.current.position.z) * 0.1
    ref.current.position.y = agent.active ? AGENT_Y + Math.sin(Date.now() * 0.003) * 0.015 : 0.08
    ref.current.rotation.z = agent.active ? 0 : 0.7
  })

  return (
    <mesh ref={ref} position={[agent.x * CELL, AGENT_Y, agent.y * CELL]}>
      <boxGeometry args={[sz, sz, sz]} />
      <meshStandardMaterial
        color={color}
        emissive={color}
        emissiveIntensity={agent.active ? 0.6 : 0.05}
        transparent
        opacity={agent.active ? 1 : 0.3}
      />
    </mesh>
  )
}

function Dispenser({ m }: { m: MapMarker }) {
  const ref = useRef<THREE.Mesh>(null!)
  const color = dispenserColors[m.type] ?? '#888'
  useFrame(() => { if (ref.current) ref.current.rotation.y += 0.008 })

  return (
    <mesh ref={ref} position={[m.x * CELL, 0.3, m.y * CELL]}>
      <boxGeometry args={[0.2, 0.55, 0.2]} />
      <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.5} />
    </mesh>
  )
}

function GoalPlane({ x, y }: { x: number; y: number }) {
  return (
    <mesh position={[x * CELL, 0.02, y * CELL]} rotation={[-Math.PI / 2, 0, 0]}>
      <planeGeometry args={[CELL, CELL]} />
      <meshStandardMaterial color="#34d399" emissive="#34d399" emissiveIntensity={0.3} transparent opacity={0.25} side={THREE.DoubleSide} />
    </mesh>
  )
}

function World() {
  const agentsMap = useHiveStore(s => s.agents)
  const agents = useMemo(() => Object.values(agentsMap), [agentsMap])
  const dispensers = useHiveStore(s => s.dispensers)
  const goals = useHiveStore(s => s.goalZones)

  return (
    <>
      <ambientLight intensity={0.5} />
      <directionalLight position={[20, 30, 15]} intensity={1} />
      <hemisphereLight args={['#334155', '#080810', 0.4]} />

      {/* floor + grid */}
      <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -0.01, 0]}>
        <planeGeometry args={[100, 100]} />
        <meshStandardMaterial color="#101018" />
      </mesh>
      <gridHelper args={[100, 100, '#2a3a4a', '#1a2535']} />

      {/* Origin marker — always visible */}
      <mesh position={[0, 0.5, 0]}>
        <sphereGeometry args={[0.15, 16, 16]} />
        <meshStandardMaterial color="#ff4444" emissive="#ff4444" emissiveIntensity={1} />
      </mesh>
      <axesHelper args={[3]} />

      {goals.map((g, i) => <GoalPlane key={i} x={g.x} y={g.y} />)}
      {dispensers.map((d, i) => <Dispenser key={i} m={d} />)}
      {agents.map(ag => <AgentBox key={ag.name} agent={ag} />)}

      <CameraRig />
    </>
  )
}

export function GridScene3D({ visible = true }: { visible?: boolean }) {
  const { connected, agentCount, dispCount, goalCount } = useHiveStore(
    useShallow(s => ({
      connected: s.connected,
      agentCount: Object.keys(s.agents).length,
      dispCount: s.dispensers.length,
      goalCount: s.goalZones.length,
    }))
  )
  const [err, setErr] = useState<string | null>(null)

  return (
    <div style={{
      width: '100%', height: 'calc(100vh - 56px)', position: visible ? 'relative' : 'absolute',
      visibility: visible ? 'visible' : 'hidden',
      pointerEvents: visible ? 'auto' : 'none',
      top: visible ? undefined : 0, left: visible ? undefined : 0,
    }}>
      {err ? (
        <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#f87171', fontFamily: 'monospace', fontSize: 12, background: '#080810' }}>
          <div style={{ textAlign: 'center' }}>
            <div>3D Error: {err}</div>
            <button onClick={() => setErr(null)} style={{ marginTop: 8, padding: '4px 12px', border: '1px solid #f87171', borderRadius: 4, color: '#f87171', background: 'transparent', cursor: 'pointer' }}>Retry</button>
          </div>
        </div>
      ) : (
        <Canvas3DErrorBoundary onError={setErr}>
          <Canvas
            camera={{ position: [5, 8, 5], fov: 60, near: 0.1, far: 500 }}
            frameloop={visible ? 'always' : 'never'}
            onCreated={({ gl, camera }) => {
              camera.lookAt(0, 0, 0)
              gl.setClearColor('#080810')
              gl.domElement.addEventListener('webglcontextlost', (e) => {
                e.preventDefault()
                setErr('WebGL context lost')
              })
            }}
          >
            <World />
          </Canvas>
        </Canvas3DErrorBoundary>
      )}

      {visible && (
        <>
          {/* HUD */}
          <div style={{ position: 'absolute', top: 8, left: 8, display: 'flex', gap: 6 }}>
            <div style={{ padding: '3px 8px', borderRadius: 4, background: 'rgba(10,10,15,0.85)', border: '1px solid rgba(34,211,238,0.2)', fontSize: 10, fontFamily: 'monospace', color: '#94a3b8' }}>
              3D · {agentCount} ag · {dispCount} disp · {goalCount} gz
            </div>
            {!connected && (
              <div style={{ padding: '3px 8px', borderRadius: 4, background: 'rgba(248,113,113,0.1)', border: '1px solid rgba(248,113,113,0.3)', fontSize: 10, fontFamily: 'monospace', color: '#f87171' }}>
                OFFLINE
              </div>
            )}
          </div>

          {/* Legend */}
          <div style={{ position: 'absolute', bottom: 8, left: 8, display: 'flex', gap: 10, padding: '4px 10px', borderRadius: 4, background: 'rgba(10,10,15,0.85)', border: '1px solid rgba(34,211,238,0.15)', fontSize: 9, fontFamily: 'monospace', color: '#64748b' }}>
            {Object.entries(roleColors).map(([r, c]) => (
              <span key={r} style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                <span style={{ width: 7, height: 7, borderRadius: 2, background: c, display: 'inline-block' }} />
                {r.replace('squad_', '')}
              </span>
            ))}
            <span style={{ borderLeft: '1px solid #334155', paddingLeft: 8, display: 'flex', alignItems: 'center', gap: 3 }}>
              <span style={{ width: 7, height: 7, borderRadius: 2, background: '#34d399', opacity: 0.5, display: 'inline-block' }} /> goal
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <span style={{ width: 7, height: 7, borderRadius: 2, background: '#ef4444', display: 'inline-block' }} /> disp
            </span>
          </div>

          <div style={{ position: 'absolute', bottom: 8, right: 8, padding: '3px 8px', borderRadius: 4, background: 'rgba(10,10,15,0.85)', border: '1px solid rgba(34,211,238,0.1)', fontSize: 9, fontFamily: 'monospace', color: '#475569' }}>
            arrastar: orbitar · direito: pan · scroll: zoom
          </div>
        </>
      )}
    </div>
  )
}
