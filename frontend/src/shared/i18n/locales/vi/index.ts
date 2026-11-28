// Chữ hiển thị tiếng Việt, chia theo khu vực. Khóa mới đặt vào file của khu vực dùng nó.
// vi và en phải có cùng các khóa (locales.test.ts).
import { common } from './common'
import { coordinatorAssignment } from './coordinatorAssignment'
import { customer } from './customer'
import { staff } from './staff'
import { staffDispatch } from './staffDispatch'
import { staffManager } from './staffManager'
import { manager } from './manager'
import { specialist } from './specialist'
import { staffReview } from './staffReview'
import { coordinator } from './coordinator'
import { fieldCrew } from './fieldCrew'
import { admin } from './admin'
import { staffCatalog } from './staffCatalog'
import { staffNetwork } from './staffNetwork'

export const vi = {
  ...common,
  ...coordinatorAssignment,
  ...customer,
  ...staff, ...staffDispatch, ...staffManager,
  ...manager,
  ...specialist,
  ...staffReview,
  ...coordinator,
  ...fieldCrew,
  ...admin,
  ...staffCatalog,
  ...staffNetwork,
} as const
