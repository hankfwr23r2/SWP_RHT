import type { ComponentType } from 'react'
import type { Role } from '../auth/role'
import type { PageLoader } from './pageData'

// Một dòng = một trang: URL, component, vai trò được vào (rỗng = công khai), tiêu đề.
// Trang có loader khai báo lazyPage thay cho page: router tải file trang song song với dữ liệu rồi mới vẽ trang.
export interface AppRoute {
  path: string
  page?: ComponentType
  lazyPage?: () => Promise<{ default: ComponentType }>
  loader?: PageLoader
  roles: Role[]
  title: string
  layout?: 'public' | 'customer' | 'staff' | 'mobile' | 'bare'
  // Không còn tác dụng: trang không còn chờ dữ liệu khởi động. Xóa ở PR dọn code cũ để không đụng routes của luồng B.
  catalog?: false
}
