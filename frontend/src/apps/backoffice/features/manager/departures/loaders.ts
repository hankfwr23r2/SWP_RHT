import { useLoaderData, useSearchParams } from 'react-router'
import { departuresApi } from '@shared/api/departures'
import { useLoad } from '@shared/hooks/useLoad'
import { loadAll } from '@shared/routing/pageData'
import { addDays, todayIso } from '@backoffice/shared/staffFormat'

const DEFAULT_DAYS = 28

export async function departuresLoader() {
  const { routes } = await loadAll({ routes: departuresApi.routes() })
  return { errors: [] as string[], routes: routes ?? [] }
}

export const useRoutesData = () => useLoaderData() as Awaited<ReturnType<typeof departuresLoader>>

// Khoảng ngày nằm trên đường dẫn để quay lại không mất bộ lọc
export function useDecisions() {
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? todayIso()
  const to = params.get('to') ?? addDays(from, DEFAULT_DAYS)
  const list = useLoad(() => departuresApi.decisions(from, to), [from, to], { toastOnError: false })
  const setRange = (patch: { from?: string; to?: string }) => {
    const next = { from, to, ...patch }
    if (next.from && next.to && next.from <= next.to) setParams(next, { replace: true })
  }
  return { from, to, setRange, ...list }
}
