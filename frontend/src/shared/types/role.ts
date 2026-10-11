export type StaffRole = 'admin' | 'manager' | 'specialist' | 'coordinator' | 'driver' | 'escort'
export type Role = 'customer' | StaffRole

export const ROLE_LABEL: Record<Role, string> = {
  customer: 'Khách hàng',
  admin: 'Quản trị viên',
  manager: 'Quản lý',
  specialist: 'Kiểm dịch viên',
  coordinator: 'Điều phối viên',
  driver: 'Tài xế',
  escort: 'Hộ tống',
}

export interface Session { role: Role; name: string; username: string }
