import type { ComponentType } from 'react'
import type { Role } from '../types/role'

// Một dòng = một trang: URL, component, vai trò được vào (rỗng = công khai), tiêu đề. example: mã mẫu thay cho :id (để sitemap mở được trang).
export interface AppRoute {
  path: string
  page: ComponentType
  roles: Role[]
  title: string
  example?: string
  layout?: 'public' | 'customer' | 'staff' | 'mobile' | 'bare'
}
