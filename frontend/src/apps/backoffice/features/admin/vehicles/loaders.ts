import { catalogLocationsApi, vehiclesApi } from '@shared/api/vehicles'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ vehicles: vehiclesApi.list(), classes: vehiclesApi.classes(), hubs: catalogLocationsApi.list('HUB') })
