import type { ComponentType } from 'react'
import type { LoaderFunction } from 'react-router'
import type { Role } from '../types/role'

// Một dòng = một trang: URL, component, vai trò được vào (rỗng = công khai), tiêu đề.
// Trang có loader khai báo lazyPage thay cho page: router tải file trang song song với dữ liệu rồi mới vẽ trang.
export interface AppRoute {
  path: string
  page?: ComponentType
  lazyPage?: () => Promise<{ default: ComponentType }>
  loader?: LoaderFunction
  roles: Role[]
  title: string
  layout?: 'public' | 'customer' | 'staff' | 'mobile' | 'bare'
}
