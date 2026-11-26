import { useTranslation } from 'react-i18next'
import { departuresApi } from '@shared/api/departures'
import { useLoad } from '@shared/hooks/useLoad'
import { useStaffAction } from '@backoffice/shared/useStaffAction'

export function useDepartureVehicles(id: number) {
  const { t } = useTranslation()
  const { data, error: loadError, loaded, reload, updateData } = useLoad(() => departuresApi.detail(id), [id])
  const { busy, error, run, clearError } = useStaffAction()

  const remove = (allotmentId: number, reason: string) => run(async () => {
    const updated = await departuresApi.removeVehicle(id, allotmentId, reason)
    updateData(() => updated)
  }, t('mgr.departures.fleet.removed'))

  return { data, loadError, loaded, reload, busy, error, remove, clearError }
}
