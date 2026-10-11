// Lấy đường bộ cho một dãy điểm: chờ 0,4 giây sau lần đổi cuối rồi gọi; trong lúc tính vẫn giữ kết quả cũ để giao diện không nhấp nháy.
import { useEffect, useState } from 'react'
import type { GeoPoint } from '../config/network'
import { roadAlternatives, roadRoute, type RoadRoute } from './routing'

export function useRoadRoute(points: GeoPoint[] | undefined, departAt?: number) {
  const key = points && points.length > 1 ? `${departAt === undefined ? '' : Math.floor(departAt / 3_600_000)}|${points.map(p => `${p.lat.toFixed(4)},${p.lng.toFixed(4)}`).join(';')}` : ''
  const [state, setState] = useState<{ key: string; route: RoadRoute | null }>({ key: '', route: null })
  useEffect(() => {
    if (!key || !points) return
    const ctl = new AbortController()
    const timer = setTimeout(() => { roadRoute(points, departAt, { signal: ctl.signal }).then(route => { if (!ctl.signal.aborted) setState({ key, route }) }) }, 400)
    return () => { clearTimeout(timer); ctl.abort() }
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps
  return { route: state.route, loading: !!key && state.key !== key }
}

// Các đường thay thế giữa hai điểm (sự cố tắc nghẽn)
export function useRoadAlternatives(from: GeoPoint | undefined, to: GeoPoint | undefined, departAt?: number) {
  const key = from && to ? `${departAt === undefined ? '' : Math.floor(departAt / 3_600_000)}|${from.lat.toFixed(4)},${from.lng.toFixed(4)}|${to.lat.toFixed(4)},${to.lng.toFixed(4)}` : ''
  const [state, setState] = useState<{ key: string; routes: RoadRoute[] }>({ key: '', routes: [] })
  useEffect(() => {
    if (!key || !from || !to) return
    const ctl = new AbortController()
    roadAlternatives(from, to, departAt, { signal: ctl.signal }).then(routes => { if (!ctl.signal.aborted) setState({ key, routes }) })
    return () => ctl.abort()
  }, [key]) // eslint-disable-line react-hooks/exhaustive-deps
  return { routes: state.routes, loading: !!key && state.key !== key }
}
