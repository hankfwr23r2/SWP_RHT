// Tài khoản hệ thống: tạo, trùng email, khóa chặn đăng nhập, mật khẩu, bảo vệ quản trị viên cuối cùng, đồng bộ danh bạ.
import { describe, expect, it } from 'vitest'
import { accountsApi } from './accounts'
import { crewApi } from './fleet'
import { staffApi } from './staff'

const ME = { name: 'Quản trị viên', email: 'admin@equine.vn' }
const fail = async (p: Promise<unknown>) => { try { await p } catch (e) { return (e as Error).message } return '' }
const byEmail = async (email: string) => (await accountsApi.list()).find(a => a.email === email)!

describe('tạo và sửa tài khoản', () => {
  it('không tạo được khi thiếu họ tên, email sai hoặc trùng email', async () => {
    expect(await fail(accountsApi.create({ name: ' ', email: 'a@b.vn', phone: '', role: 'manager' }, 'Admin'))).toMatch(/họ tên/)
    expect(await fail(accountsApi.create({ name: 'Người Mới', email: 'khong-hop-le', phone: '', role: 'manager' }, 'Admin'))).toMatch(/Email/)
    expect(await fail(accountsApi.create({ name: 'Người Mới', email: 'MANAGER@equine.vn', phone: '', role: 'manager' }, 'Admin'))).toMatch(/đã có tài khoản/)
  })
  it('tạo kiểm dịch viên mới: có mật khẩu tạm, vào danh bạ để được phân công, đăng nhập đúng mật khẩu', async () => {
    const { account, password } = await accountsApi.create({ name: 'Bùi Thị Kiểm Mới', email: 'kiemmoi@equine.vn', phone: '0907 000 111', role: 'specialist' }, 'Admin')
    expect(account.status).toBe('active')
    expect(password.length).toBeGreaterThanOrEqual(8)
    expect((await staffApi.list()).find(s => s.name === 'Bùi Thị Kiểm Mới')).toMatchObject({ role: 'inspector', status: 'working' })
    expect(await accountsApi.authenticate('kiemmoi@equine.vn', 'sai', 'staff')).toMatchObject({ ok: false, reason: 'Sai mật khẩu.' })
    expect(await accountsApi.authenticate('kiemmoi@equine.vn', password, 'staff')).toMatchObject({ ok: true })
  })
  it('tạo tài xế mới thì vào danh bạ tài xế; trùng họ tên nhân viên thì từ chối', async () => {
    await accountsApi.create({ name: 'Tài Xế Mới Tinh', email: 'taixemoi@equine.vn', phone: '0907 222 333', role: 'driver' }, 'Admin')
    expect((await crewApi.list()).find(c => c.name === 'Tài Xế Mới Tinh')).toMatchObject({ role: 'driver' })
    expect(await fail(accountsApi.create({ name: 'Tài Xế Mới Tinh', email: 'khac@equine.vn', phone: '', role: 'driver' }, 'Admin'))).toMatch(/trùng họ tên/)
  })
  it('nhân viên vận hành không đổi được họ tên, nhưng đổi được số điện thoại và email', async () => {
    const a = await byEmail('specialist@equine.vn')
    const out = await accountsApi.update(a.id, { name: 'Tên Khác', email: 'specialist@equine.vn', phone: '0999 999 999' }, 'Admin')
    expect(out.name).toBe(a.name)
    expect(out.phone).toBe('0999 999 999')
    expect(out.history.at(-1)!.text).toMatch(/số điện thoại/)
  })
})

describe('khóa và mở khóa', () => {
  it('tài khoản bị khóa không đăng nhập được; mở khóa thì vào lại', async () => {
    const a = await byEmail('driver@equine.vn')
    await accountsApi.setLocked(a.id, true, ME, 'Nghỉ việc')
    expect(await accountsApi.authenticate('driver@equine.vn', 'x', 'staff')).toMatchObject({ ok: false, reason: expect.stringMatching(/bị khóa/) })
    await accountsApi.setLocked(a.id, false, ME)
    expect(await accountsApi.authenticate('driver@equine.vn', 'x', 'staff')).toMatchObject({ ok: true })
    expect((await byEmail('driver@equine.vn')).history.map(h => h.text)).toEqual(expect.arrayContaining([expect.stringMatching(/Khóa tài khoản: Nghỉ việc/), 'Mở khóa tài khoản']))
  })
  it('khóa điều phối viên thì danh bạ chuyển sang nghỉ để hệ thống không phân công; mở khóa thì làm việc lại', async () => {
    const a = await byEmail('ops@equine.vn')
    await accountsApi.setLocked(a.id, true, ME)
    expect((await staffApi.list()).find(s => s.name === a.name)!.status).toBe('off')
    await accountsApi.setLocked(a.id, false, ME)
    expect((await staffApi.list()).find(s => s.name === a.name)!.status).toBe('working')
  })
  it('không tự khóa mình và không khóa quản trị viên cuối cùng', async () => {
    const admin = await byEmail('admin@equine.vn')
    expect(await fail(accountsApi.setLocked(admin.id, true, ME))).toMatch(/chính tài khoản của bạn/)
    expect(await fail(accountsApi.setLocked(admin.id, true, { name: 'Khác', email: 'khac@equine.vn' }))).toMatch(/cuối cùng/)
  })
  it('tài khoản khách bị khóa không vào được trang khách; tài khoản nội bộ không vào trang khách', async () => {
    expect(await accountsApi.authenticate('phuongnam.club@gmail.com', 'x', 'customer')).toMatchObject({ ok: false, reason: expect.stringMatching(/bị khóa/) })
    expect(await accountsApi.authenticate('manager@equine.vn', 'x', 'customer')).toMatchObject({ ok: false })
    expect(await accountsApi.authenticate('longthanh.farm@gmail.com', 'x', 'staff')).toMatchObject({ ok: false })
    expect(await accountsApi.authenticate('email.chua.co@gmail.com', 'x', 'customer')).toMatchObject({ ok: true })
  })
})

describe('đặt lại mật khẩu', () => {
  it('mật khẩu cũ mất hiệu lực, mật khẩu tạm mới đăng nhập được, có ghi lịch sử', async () => {
    const a = await byEmail('escort@equine.vn')
    const password = await accountsApi.resetPassword(a.id, 'Admin')
    expect(await accountsApi.authenticate('escort@equine.vn', 'mat-khau-cu', 'staff')).toMatchObject({ ok: false, reason: 'Sai mật khẩu.' })
    expect(await accountsApi.authenticate('escort@equine.vn', password, 'staff')).toMatchObject({ ok: true })
    expect((await byEmail('escort@equine.vn')).history.at(-1)!.text).toBe('Đặt lại mật khẩu')
  })
  it('danh sách trả về không lộ mật khẩu', async () => {
    expect((await accountsApi.list()).every(a => !('password' in a))).toBe(true)
  })
})
