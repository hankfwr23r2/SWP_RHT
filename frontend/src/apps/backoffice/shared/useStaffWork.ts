import { useEffect, useSyncExternalStore } from 'react'
import { fetchStaffWork, type StaffWorkSummary } from '@shared/api/staffWork'
import { useAuth } from '@shared/auth/AuthContext'

const REFRESH_MS = 60_000
const listeners = new Set<() => void>()
let owner: string | undefined
let snapshot: StaffWorkSummary | undefined
let request: Promise<void> | undefined
let version = 0
let consumers = 0
let stopRefresh: (() => void) | undefined

function publish() {
  listeners.forEach(listener => listener())
}

function refresh() {
  if (request || !owner || document.hidden) return
  const started = version
  request = fetchStaffWork().then(data => {
    if (started === version) {
      snapshot = data
      publish()
    }
  }).catch(() => undefined).finally(() => {
    request = undefined
    if (started !== version) refresh()
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function invalidateStaffWork() {
  if (!owner) return
  version += 1
  refresh()
}

export function useStaffWork() {
  const { session } = useAuth()
  const key = session && (session.role === 'manager' || session.role === 'coordinator')
    ? `${session.role}:${session.username}` : undefined
  const work = useSyncExternalStore(subscribe, () => key && key === owner ? snapshot : undefined)

  useEffect(() => {
    if (!key) return
    consumers += 1
    if (owner !== key) {
      owner = key
      snapshot = undefined
      version += 1
      publish()
      refresh()
    }
    if (!stopRefresh) {
      const timer = window.setInterval(refresh, REFRESH_MS)
      document.addEventListener('visibilitychange', refresh)
      stopRefresh = () => {
        window.clearInterval(timer)
        document.removeEventListener('visibilitychange', refresh)
      }
    }
    return () => {
      consumers -= 1
      if (consumers === 0) {
        stopRefresh?.()
        stopRefresh = undefined
        owner = undefined
        snapshot = undefined
        version += 1
      }
    }
  }, [key])
  return work
}
