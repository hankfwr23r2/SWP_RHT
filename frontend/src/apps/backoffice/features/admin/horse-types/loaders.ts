import { customerHorseApi } from '@shared/api/horses'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ horseTypes: customerHorseApi.listTypes() })
