// Service tài khoản hệ thống (quản trị viên), gọi Backend Spring Boot (/admin/accounts). Không giữ dữ liệu ở máy khách.
import { adminAccountsApi, type AccountApiItem } from '../api/adminAccounts'
import type { Account } from '../types/account'
import type { Role } from '../types/role'

export interface AccountInput { name: string; email: string; phone: string; role: Role }

const norm = (email: string) => email.trim().toLowerCase()
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const OPS: Role[] = ['specialist', 'coordinator', 'driver', 'escort'] // tên gắn với đơn và lịch phân công nên không đổi sau khi tạo
export const isOpsRole = (r: Role) => OPS.includes(r)
const userIdOf = (id: string) => Number(id.replace(/\D/g, ''))

const accountOf = (item: AccountApiItem): Account => ({
  id: item.id || `TK-${item.userId}`, name: item.name, email: item.email, phone: item.phone ?? '', role: item.role, status: item.status,
  createdAt: item.hireDate ? new Date(item.hireDate).getTime() : 0, history: [],
})

export const accountsApi = {
  list: async (): Promise<Account[]> => (await adminAccountsApi.list()).map(accountOf),

  // Tạo tài khoản nhân viên: trả về mật khẩu tạm (chỉ hiện một lần). Nhân viên vận hành tự vào danh bạ phía backend.
  create: async (input: AccountInput, _actor: string): Promise<{ account: Account; password: string }> => {
    const name = input.name.trim()
    const email = norm(input.email)
    if (!name) throw new Error('Cần nhập họ tên.')
    if (!EMAIL.test(email)) throw new Error('Email không hợp lệ.')
    const res = await adminAccountsApi.create({ name, email, phone: input.phone, role: input.role })
    if (!res.tempPassword) throw new Error('Backend không trả mật khẩu tạm cho tài khoản mới.')
    return { account: accountOf(res), password: res.tempPassword }
  },

  // Cập nhật thông tin tài khoản: họ tên, email, số điện thoại
  update: async (id: string, patch: Pick<AccountInput, 'name' | 'email' | 'phone'>, _actor: string): Promise<Account> => {
    const name = patch.name.trim()
    const email = norm(patch.email)
    if (!name) throw new Error('Cần nhập họ tên.')
    if (!EMAIL.test(email)) throw new Error('Email không hợp lệ.')
    const res = await adminAccountsApi.update(userIdOf(id), { name, email, phone: patch.phone?.trim() })
    return accountOf(res)
  },

  // Khóa / mở khóa. Không tự khóa mình và không khóa quản trị viên cuối cùng.
  setLocked: async (id: string, locked: boolean, actor: { name: string; email: string }, reason = ''): Promise<Account> => {
    const all = await accountsApi.list()
    const a = all.find(x => x.id === id)
    if (!a) throw new Error(`Không tìm thấy tài khoản ${id}.`)
    if (locked && a.email === norm(actor.email)) throw new Error('Không thể khóa chính tài khoản của bạn.')
    if (locked && a.role === 'admin' && all.filter(x => x.role === 'admin' && x.status === 'active').length < 2) throw new Error('Không thể khóa quản trị viên cuối cùng.')
    if ((a.status === 'locked') !== locked) await adminAccountsApi.toggleLock(userIdOf(id), locked, reason.trim() || undefined)
    return { ...a, status: locked ? 'locked' : 'active' }
  },

  // Đặt lại mật khẩu: trả về mật khẩu tạm (chỉ hiện một lần)
  resetPassword: async (id: string, _actor: string): Promise<string> => adminAccountsApi.resetPassword(userIdOf(id)),
}
