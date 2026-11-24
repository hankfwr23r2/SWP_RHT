import { priceListsApi, type PriceItemCreate, type PriceListCreate } from '@shared/api/priceLists'
import { useStaffFormAction } from '@shared/hooks/useStaffFormAction'

export function usePricingActions() {
  const action = useStaffFormAction()
  const { run } = action
  return {
    ...action,
    saveList: (body: PriceListCreate, id?: number) => run(() => id == null ? priceListsApi.create(body) : priceListsApi.update(id, body)),
    activate: (id: number) => run(() => priceListsApi.activate(id)),
    archive: (id: number) => run(() => priceListsApi.archive(id)),
    remove: (id: number) => run(() => priceListsApi.remove(id), false),
    saveItem: (id: number, body: PriceItemCreate, itemId?: number) =>
      run(() => itemId == null ? priceListsApi.createItem(id, body) : priceListsApi.updateItem(id, itemId, body)),
    removeItem: (id: number, itemId: number) => run(() => priceListsApi.removeItem(id, itemId)),
  }
}
