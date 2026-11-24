// Việc đang chờ quản lý, dùng cho số đếm ở menu và thanh việc gấp. Hai nơi dùng chung một lần tải.
import { useEffect, useState } from 'react'
import { departuresApi } from '@shared/api/departures'
import { managerOrdersApi, SUMMARY_PAGE_SIZE } from '@shared/api/managerOrders'
import { refundsApi } from '@shared/api/refunds'
import { addDays, todayIso } from './staffFormat'

export interface ManagerWork {
  quoting: number
  documents: number
  decisions: number
  refunds: number
}

const TTL_MS = 30_000
const REFRESH_MS = 60_000
const WEEK = 7
let cached: { at: number; work: Promise<ManagerWork> } | undefined

// Một nguồn lỗi thì coi như 0, không làm hỏng menu
const count = (source: Promise<unknown[]>) => source.then(rows => rows.length, () => 0)

export function loadManagerWork(now = Date.now()): Promise<ManagerWork> {
  if (cached && now - cached.at < TTL_MS) return cached.work
  const today = todayIso(now)
  const work = Promise.all([
    count(managerOrdersApi.summaries(['QUOTING'], 0)),
    count(managerOrdersApi.missingDocuments()),
    count(departuresApi.decisions(today, addDays(today, WEEK)).then(rows => rows.filter(row => row.confirmable))),
    count(refundsApi.list('PENDING')),
  ]).then(([quoting, documents, decisions, refunds]) => ({ quoting, documents, decisions, refunds }))
  cached = { at: now, work }
  return work
}

export const resetManagerWork = () => { cached = undefined }

// Số từ trang đầu: đủ một trang thì còn nữa
export const countLabel = (value: number) => value >= SUMMARY_PAGE_SIZE ? `${SUMMARY_PAGE_SIZE}+` : String(value)

export function useManagerWork(enabled: boolean): ManagerWork | undefined {
  const [work, setWork] = useState<ManagerWork>()
  useEffect(() => {
    if (!enabled) return
    let active = true
    const refresh = () => { if (!document.hidden) void loadManagerWork().then(next => { if (active) setWork(next) }) }
    refresh()
    const timer = window.setInterval(refresh, REFRESH_MS)
    return () => { active = false; window.clearInterval(timer) }
  }, [enabled])
  return enabled ? work : undefined
}
