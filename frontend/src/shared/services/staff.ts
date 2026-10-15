// Service nhân sự nội bộ (kiểm dịch viên, điều phối viên), gọi Backend Spring Boot (/fleet/crew). Không giữ dữ liệu ở máy khách.
import { backendFleetApi } from '../api/fleet'
import type { StaffMember } from '../types/staff'

export const staffApi = {
  list: async (): Promise<StaffMember[]> => (await backendFleetApi.getCrew()).flatMap(member => {
    const role = member.role.toUpperCase()
    if (role !== 'SPECIALIST' && role !== 'INSPECTOR' && role !== 'COORDINATOR') return []
    return [{
      id: member.staffCode || String(member.userId),
      name: member.name,
      phone: member.phone || '',
      role: role === 'COORDINATOR' ? 'coordinator' : 'inspector',
      status: member.employmentStatus.toUpperCase() === 'ACTIVE' ? 'working' : 'off',
    }]
  }),
  // Nhân sự do quản trị viên tạo hoặc khóa tài khoản (xem accounts.ts), không sửa ở đây
  update: async (_id: string, _patch: Partial<StaffMember>): Promise<StaffMember> => {
    throw new Error('Cập nhật nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
  create: async (_data: Pick<StaffMember, 'name' | 'phone' | 'role'>): Promise<StaffMember> => {
    throw new Error('Tạo nhân sự phải thực hiện qua API quản lý tài khoản.')
  },
}
