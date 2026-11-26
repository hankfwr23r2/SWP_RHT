import { staffAccountsApi } from '@shared/api/staffAccounts'
import { catalogLocationsApi } from '@shared/api/vehicles'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ accounts: staffAccountsApi.list(), hubs: catalogLocationsApi.list('HUB') })
