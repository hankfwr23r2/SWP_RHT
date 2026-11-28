import { catalogApi } from '@shared/api/catalog'
import { regulationsApi } from '@shared/api/regulations'
import { catalogLocationsApi } from '@shared/api/vehicles'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ types: regulationsApi.types(), rules: regulationsApi.rules(), countries: catalogApi.countries(), gates: catalogLocationsApi.list('BORDER_GATE') })
