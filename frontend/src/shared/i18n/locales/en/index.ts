// Chữ hiển thị tiếng Anh, chia theo khu vực. Khóa mới đặt vào file của khu vực dùng nó.
// vi và en phải có cùng các khóa (locales.test.ts).
import { common } from './common'
import { customer } from './customer'
import { staff } from './staff'
import { manager } from './manager'
import { specialist } from './specialist'
import { coordinator } from './coordinator'
import { fieldCrew } from './fieldCrew'
import { admin } from './admin'
import { staffNetwork } from './staffNetwork'

export const en = {
  ...common,
  ...customer,
  ...staff,
  ...manager,
  ...specialist,
  ...coordinator,
  ...fieldCrew,
  ...admin,
  ...staffNetwork,
} as const
