import { hubStallsApi, type HubStallCreate } from '@shared/api/hubStalls'
import { useLoad } from '@shared/hooks/useLoad'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'

export function useHubStallAdmin(hubId: number) {
  const inventory = useLoad(() => hubStallsApi.inventory(hubId), [hubId], { toastOnError: false })
  const { busy, fields, reset, run } = useStaffFormAction()

  function update(action: () => Promise<unknown>) {
    return run(async () => {
      await action()
      await inventory.reload()
    }, false)
  }

  return {
    ...inventory, busy, fields, reset,
    create: (body: HubStallCreate) => update(() => hubStallsApi.create(hubId, body)),
    deactivate: (stallId: number) => update(() => hubStallsApi.deactivate(hubId, stallId)),
  }
}
