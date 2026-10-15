import type { ComponentType } from 'react'
import type { Role } from '../types/role'

// Một dòng = một trang: URL, component, vai trò được vào (rỗng = công khai), tiêu đề.
export interface AppRoute {
  path: string
  page: ComponentType
  roles: Role[]
  title: string
  layout?: 'public' | 'customer' | 'staff' | 'mobile' | 'bare'
}
