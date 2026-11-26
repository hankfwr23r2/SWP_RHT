import { vehiclesApi } from '@shared/api/vehicles'
import { catalogApi } from '@shared/api/catalog'
import { networkApi } from '@shared/api/network'
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'

function load<T extends Record<string, Promise<unknown>>>(sources: T) {
  return Promise.resolve({ errors: [] as string[], pending: loadAll(sources) })
}

export const locationsLoader = () => load({ locations: networkApi.locations(), countries: catalogApi.countries() })
export const areasLoader = () => load({
  areas: networkApi.areas(), provinces: catalogApi.provinces('VN'), hubs: networkApi.locations({ type: 'HUB', status: 'ACTIVE' }),
})
export const routesLoader = () => load({ routes: networkApi.routes() })
export const routeEditorLoader = ({ params }: PageLoaderContext) => load({
  locations: networkApi.locations(), route: params.id === undefined ? Promise.resolve(null) : networkApi.route(Number(params.id)),
})
export const schedulesLoader = () => load({ schedules: networkApi.schedules(), routes: networkApi.routes(), classes: vehiclesApi.classes() })
export const departuresLoader = () => load({ departures: networkApi.departures() })

export type NetworkResult<L extends (...args: never[]) => Promise<{ pending: Promise<unknown> }>> = Awaited<Awaited<ReturnType<L>>['pending']>
