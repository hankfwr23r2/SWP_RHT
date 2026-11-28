import { settingsApi } from '@shared/api/settings'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ settings: settingsApi.list() })
