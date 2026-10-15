// Service tài khoản hệ thống (quản trị viên). Sau này: GET/POST/PATCH /api/accounts, POST /api/auth/login.
// Tạo tài khoản nhân viên thì đồng bộ vào danh bạ (nhân sự / tài xế / hộ tống) để được phân công; khóa kiểm dịch viên / điều phối viên thì danh bạ chuyển sang nghỉ.
import type { Role } from '../types/role'
import { crewApi } from './fleet'
import { seedAccounts, type Account } from './mock/accounts'
import { staffApi } from './staff'
import { createStore } from './store'

const store = createStore<Account>('accounts', seedAccounts)

export interface AccountInput { name: string; email: string; phone: string; role: Role }
export type AuthResult = { ok: true; account?: Account } | { ok: false; reason: string }

const norm = (email: string) => email.trim().toLowerCase()
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const OPS: Role[] = ['specialist', 'coordinator', 'driver', 'escort'] // tên gắn với đơn và lịch phân công nên không đổi sau khi tạo
export const isOpsRole = (r: Role) => OPS.includes(r)
const log = (a: Account, actor: string, text: string) => [...a.history, { time: Date.now(), actor, text }]
const must = (id: string) => { const a = store.get(id); if (!a) throw new Error(`Không tìm thấy tài khoản ${id}.`); return a }
const tempPassword = () => `Eq${Math.random().toString(36).slice(2, 8)}${Math.floor(10 + Math.random() * 90)}`
const activeAdmins = () => store.all().filter(a => a.role === 'admin' && a.status === 'active')

import { adminAccountsApi } from '../api/adminAccounts'

export const accountsApi = {
  list: async (): Promise<Account[]> => {
    if (typeof window !== 'undefined') {
      try {
        const apiList = await adminAccountsApi.list()
        if (apiList && apiList.length > 0) {
          return apiList.map(item => ({
            id: item.id || `TK-${item.userId}`,
            name: item.name,
            email: item.email,
            phone: item.phone || '',
            role: item.role,
            status: item.status,
            createdAt: item.hireDate ? new Date(item.hireDate).getTime() : Date.now(),
            history: [],
          }))
        }
      } catch {
        // Dùng mock store khi backend offline
      }
    }
    return structuredClone(store.all()).map(({ password: _p, ...a }) => a)
  },
  // Đăng nhập: tài khoản bị khóa không vào được; tài khoản có mật khẩu (tạo hoặc đặt lại) phải đúng mật khẩu; tài khoản mẫu nhận mọi mật khẩu.
  // Email chưa có trong hệ thống: giữ cách giả lập cũ (cổng nhân viên đoán vai trò theo từ khóa trong email).
  authenticate: async (email: string, password: string, portal: 'staff' | 'customer'): Promise<AuthResult> => {
    const a = store.all().find(x => x.email === norm(email))
    if (!a) return { ok: true }
    if (a.status === 'locked') return { ok: false, reason: 'Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.' }
    if (a.password && a.password !== password) return { ok: false, reason: 'Sai mật khẩu.' }
    if (portal === 'staff' && a.role === 'customer') return { ok: false, reason: 'Đây là tài khoản khách hàng, hãy đăng nhập ở trang khách hàng.' }
    if (portal === 'customer' && a.role !== 'customer') return { ok: false, reason: 'Đây là tài khoản nội bộ, hãy đăng nhập ở cổng nhân viên.' }
    const out = store.update(a.id, { lastLoginAt: Date.now() })
    return { ok: true, account: structuredClone(out) }
  },

  // Tạo tài khoản nhân viên: trả về mật khẩu tạm (chỉ hiện một lần)
  create: async (input: AccountInput, actor: string): Promise<{ account: Account; password: string }> => {
    const name = input.name.trim()
    const email = norm(input.email)
    if (!name) throw new Error('Cần nhập họ tên.')
    if (!EMAIL.test(email)) throw new Error('Email không hợp lệ.')

    if (typeof window !== 'undefined') {
      try {
        const res = await adminAccountsApi.create({
          name,
          email,
          phone: input.phone,
          role: input.role,
        })
        const account: Account = {
          id: res.id,
          name: res.name,
          email: res.email,
          phone: res.phone || '',
          role: res.role,
          status: res.status,
          createdAt: Date.now(),
          history: [{ time: Date.now(), actor, text: 'Tạo tài khoản' }],
        }
        return { account, password: res.tempPassword || tempPassword() }
      } catch (err) {
        if (err instanceof Error && !err.message.includes('Network Error')) {
          throw err
        }
      }
    }
    if (store.all().some(a => a.email === email)) throw new Error('Email này đã có tài khoản.')
    if (store.all().some(a => a.name === name && isOpsRole(input.role) && isOpsRole(a.role))) throw new Error('Đã có nhân viên trùng họ tên. Họ tên gắn với đơn và lịch phân công nên không được trùng.')
    const next = Math.max(0, ...store.all().map(a => Number(a.id.slice(3)))) + 1
    const password = tempPassword()
    const now = Date.now()
    const account: Account = { id: `TK-${String(next).padStart(3, '0')}`, name, email, phone: input.phone.trim(), role: input.role, status: 'active', createdAt: now, password, history: [{ time: now, actor, text: 'Tạo tài khoản' }] }
    store.add(account)
    if (input.role === 'specialist' || input.role === 'coordinator') await staffApi.create({ name, phone: account.phone, role: input.role === 'specialist' ? 'inspector' : 'coordinator' })
    if (input.role === 'driver' || input.role === 'escort') await crewApi.create({ name, phone: account.phone, role: input.role })
    return { account: structuredClone(account), password }
  },

  // Sửa thông tin: không đổi vai trò; nhân viên vận hành không đổi họ tên
  update: async (id: string, patch: Pick<AccountInput, 'name' | 'email' | 'phone'>, actor: string): Promise<Account> => {
    const a = must(id)
    const email = norm(patch.email)
    const name = isOpsRole(a.role) ? a.name : patch.name.trim()
    if (!name) throw new Error('Cần nhập họ tên.')
    if (!EMAIL.test(email)) throw new Error('Email không hợp lệ.')
    if (store.all().some(x => x.id !== id && x.email === email)) throw new Error('Email này đã có tài khoản.')
    const changes = [name !== a.name && 'họ tên', email !== a.email && 'email', patch.phone.trim() !== a.phone && 'số điện thoại'].filter(Boolean)
    if (!changes.length) return structuredClone(a)
    return structuredClone(store.update(id, { name, email, phone: patch.phone.trim(), history: log(a, actor, `Cập nhật ${changes.join(', ')}`) }))
  },

  // Khóa / mở khóa. Không tự khóa mình và không khóa quản trị viên cuối cùng.
  setLocked: async (id: string, locked: boolean, actor: { name: string; email: string }, reason = ''): Promise<Account> => {
    const a = must(id)
    if (locked && a.email === norm(actor.email)) throw new Error('Không thể khóa chính tài khoản của bạn.')
    if (locked && a.role === 'admin' && activeAdmins().length < 2) throw new Error('Không thể khóa quản trị viên cuối cùng.')
    if ((a.status === 'locked') === locked) return structuredClone(a)

    const userIdMatch = id.match(/\d+/)
    if (typeof window !== 'undefined' && userIdMatch) {
      try {
        await adminAccountsApi.toggleLock(Number(userIdMatch[0]), locked)
      } catch {
        // Dùng mock store khi chạy test hoặc offline
      }
    }

    const out = store.update(id, { status: locked ? 'locked' : 'active', history: log(a, actor.name, locked ? `Khóa tài khoản${reason.trim() ? `: ${reason.trim()}` : ''}` : 'Mở khóa tài khoản') })
    if (a.role === 'specialist' || a.role === 'coordinator') {
      const person = (await staffApi.list()).find(s => s.name === a.name)
      if (person) await staffApi.update(person.id, locked ? { status: 'off', offReason: 'Tài khoản bị khóa' } : { status: 'working', offReason: undefined, offTo: undefined })
    }
    return structuredClone(out)
  },

  // Đặt lại mật khẩu: trả về mật khẩu tạm (chỉ hiện một lần)
  resetPassword: async (id: string, actor: string): Promise<string> => {
    const userIdMatch = id.match(/\d+/)
    if (typeof window !== 'undefined' && userIdMatch) {
      try {
        const pass = await adminAccountsApi.resetPassword(Number(userIdMatch[0]))
        if (pass) return pass
      } catch {
        // Dùng mock store khi chạy test hoặc offline
      }
    }

    const a = must(id)
    const password = tempPassword()
    store.update(id, { password, history: log(a, actor, 'Đặt lại mật khẩu') })
    return password
  },
}
