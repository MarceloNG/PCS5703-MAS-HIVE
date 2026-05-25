import { useEffect, useRef } from 'react'
import { useHiveStore } from './store'
import type { HiveMessage } from './types'

const WS_URL = 'ws://localhost:8765'
const RECONNECT_BASE = 1000
const RECONNECT_MAX = 10000

export function useHiveSocket() {
  const wsRef = useRef<WebSocket | null>(null)
  const retriesRef = useRef(0)
  const { setConnected, applySnapshot, applyEvent } = useHiveStore()

  useEffect(() => {
    let disposed = false
    let timer: ReturnType<typeof setTimeout>

    function connect() {
      if (disposed) return
      const ws = new WebSocket(WS_URL)
      wsRef.current = ws

      ws.onopen = () => {
        retriesRef.current = 0
        setConnected(true)
      }

      ws.onmessage = (msg) => {
        try {
          const data = JSON.parse(msg.data)
          if (data.type === 'snapshot') {
            applySnapshot(data as HiveMessage)
          } else if (data.type === 'event') {
            applyEvent(data as HiveMessage)
          } else if (data.type === 'step_update') {
            useHiveStore.setState({ step: data.step, score: data.score })
          }
        } catch { /* ignore malformed */ }
      }

      ws.onclose = () => {
        setConnected(false)
        if (!disposed) {
          const delay = Math.min(RECONNECT_BASE * 2 ** retriesRef.current, RECONNECT_MAX)
          retriesRef.current++
          timer = setTimeout(connect, delay)
        }
      }

      ws.onerror = () => ws.close()
    }

    connect()

    return () => {
      disposed = true
      clearTimeout(timer)
      wsRef.current?.close()
    }
  }, [setConnected, applySnapshot, applyEvent])
}
