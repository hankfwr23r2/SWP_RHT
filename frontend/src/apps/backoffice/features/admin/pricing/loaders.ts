import { catalogApi } from '@shared/api/catalog'
import { networkApi } from '@shared/api/network'
import { priceListsApi } from '@shared/api/priceLists'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

export const pricingLoader = async () => ({ errors: [] as string[], pending: loadAll({ lists: priceListsApi.list() }) })
export const pricingDetailLoader = async ({ params }: PageLoaderContext) => {
  const id = Number(params.id)
  return {
    errors: [] as string[],
    pending: loadAll({
      list: priceListsApi.detail(id), items: priceListsApi.items(id), services: catalogApi.billableServices(),
      routes: networkApi.routes(), stallTypes: catalogApi.stallTypes(),
      classes: priceListsApi.vehicleClasses(),
    }),
  }
}

export type PricingData = Awaited<Awaited<ReturnType<typeof pricingLoader>>['pending']>
export type PricingDetailData = Awaited<Awaited<ReturnType<typeof pricingDetailLoader>>['pending']>
