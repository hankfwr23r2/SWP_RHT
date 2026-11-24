import { catalogApi } from '@shared/api/catalog'
import { partiesApi } from '@shared/api/parties'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ parties: partiesApi.list(), countries: catalogApi.countries() })
