// Chữ hiển thị tiếng Việt, chia theo khu vực. Khóa mới đặt vào file của khu vực dùng nó.
// vi và en phải có cùng các khóa (locales.test.ts).
import { common } from './common'
import { customer } from './customer'
import { staff } from './staff'
import { staffManager } from './staffManager'
import { manager } from './manager'
import { specialist } from './specialist'
import { coordinator } from './coordinator'
import { fieldCrew } from './fieldCrew'
import { admin } from './admin'
import { staffCatalog } from './staffCatalog'

export const vi = {
  ...common,
  ...customer,
  ...staff, ...staffManager,
  ...manager,
  ...specialist,
  ...coordinator,
  ...fieldCrew,
  ...admin,
  ...staffCatalog,
} as const
