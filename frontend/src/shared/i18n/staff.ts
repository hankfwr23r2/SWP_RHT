// Chữ của app nội bộ, thêm vào bộ chữ chung. Gói của khách không tải file này.
import i18n, { enParts } from './index'
import { admin } from './locales/vi/admin'
import { staffCatalog } from './locales/vi/staffCatalog'
import { coordinator } from './locales/vi/coordinator'
import { fieldCrew } from './locales/vi/fieldCrew'
import { manager } from './locales/vi/manager'
import { specialist } from './locales/vi/specialist'
import { staff } from './locales/vi/staff'
import { staffNetwork } from './locales/vi/staffNetwork'

i18n.addResourceBundle('vi', 'translation', { ...staff, ...manager, ...specialist, ...coordinator, ...fieldCrew, ...admin, ...staffCatalog, ...staffNetwork }, true)

enParts.push(
  () => import('./locales/en/staff').then(m => m.staff),
  () => import('./locales/en/manager').then(m => m.manager),
  () => import('./locales/en/specialist').then(m => m.specialist),
  () => import('./locales/en/coordinator').then(m => m.coordinator),
  () => import('./locales/en/fieldCrew').then(m => m.fieldCrew),
  () => import('./locales/en/admin').then(m => m.admin),
  () => import('./locales/en/staffCatalog').then(m => m.staffCatalog),
  () => import('./locales/en/staffNetwork').then(m => m.staffNetwork),
)
