import { useLoad } from '@shared/hooks/useLoad'
import { auditLogsApi } from '@shared/api/auditLogs'
import { auditQuery } from '@shared/lib/systemLogs'

export function useAuditLogs(params: URLSearchParams, defaults: { from: string; to: string }) {
  const search = new URLSearchParams(params)
  search.delete('log')
  const queryKey = search.toString()
  return useLoad(async () => {
    const query = auditQuery(new URLSearchParams(queryKey), defaults)
    return auditLogsApi.list({ ...query, from: String(query.from), to: String(query.to) })
  }, [queryKey, defaults.from, defaults.to], { toastOnError: false })
}

export function useAuditDetail(id: string) {
  return useLoad(() => auditLogsApi.detail(id), [id], { toastOnError: false })
}
