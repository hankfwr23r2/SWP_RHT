import { auditLogsApi } from '@shared/api/auditLogs'
import { loadCatalog } from '@backoffice/shared/useCatalogData'

export const loader = () => loadCatalog({ options: auditLogsApi.options() })
