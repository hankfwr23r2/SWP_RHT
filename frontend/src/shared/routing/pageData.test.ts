import { afterEach, describe, expect, it, vi } from 'vitest'
import { guardLoader, loadAll } from './pageData'

const signIn = (app: string, session: object | null) => {
  const store = new Map<string, string>()
  if (session) store.set(`SWP_SESSION_${app}`, JSON.stringify(session))
  vi.stubGlobal('sessionStorage', { getItem: (k: string) => store.get(k) ?? null })
}

describe('dữ liệu trang do router tải', () => {
  afterEach(() => { vi.unstubAllGlobals() })

  it('chờ song song mọi nguồn và trả đúng theo tên', async () => {
    expect(await loadAll({ orders: Promise.resolve([1, 2]), horses: Promise.resolve(['a']) }))
      .toEqual({ orders: [1, 2], horses: ['a'], errors: [] })
  })

  it('một nguồn lỗi thì vẫn giữ nguồn kia và trả thông báo lỗi để trang báo', async () => {
    expect(await loadAll({ orders: Promise.reject(new Error('Không tìm thấy API: /customer/incidents')), horses: Promise.resolve(['a']) }))
      .toEqual({ orders: undefined, horses: ['a'], errors: ['Không tìm thấy API: /customer/incidents'] })
  })

  it('chưa đăng nhập thì không gọi loader, để RequireRole chuyển về trang đăng nhập', async () => {
    signIn('customer', null)
    const loader = vi.fn()
    expect(guardLoader(loader, ['customer'], 'customer')({ params: {} })).toBeNull()
    expect(loader).not.toHaveBeenCalled()
  })

  it('đăng nhập sai vai trò thì cũng không gọi loader', async () => {
    signIn('backoffice', { role: 'driver', name: 'Tài xế', username: 'driver01' })
    const loader = vi.fn()
    expect(guardLoader(loader, ['manager'], 'backoffice')({ params: {} })).toBeNull()
    expect(loader).not.toHaveBeenCalled()
  })

  it('đúng vai trò thì gọi loader với tham số URL và phiên đang đăng nhập', async () => {
    const session = { role: 'specialist', name: 'Kiểm dịch viên', username: 'sp01' }
    signIn('backoffice', session)
    const loader = vi.fn().mockResolvedValue({ errors: [] })
    await guardLoader(loader, ['specialist'], 'backoffice')({ params: { id: 'BK-001' } })
    expect(loader).toHaveBeenCalledWith({ params: { id: 'BK-001' }, session })
  })
})
