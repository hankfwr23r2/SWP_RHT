import { beforeEach, describe, expect, it, vi } from 'vitest'

const store = new Map<string, string>()
const signIn = (username: string) => store.set('SWP_SESSION_customer', JSON.stringify({ role: 'customer', username }))

describe('bản nháp đơn', () => {
  beforeEach(() => {
    store.clear()
    vi.resetModules()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => { store.set(key, value) },
      removeItem: (key: string) => { store.delete(key) },
    })
  })

  it('giữ riêng theo tài khoản, người đăng nhập sau không thấy bản nháp của người trước', async () => {
    const { saveDraft } = await import('./draft')
    signIn('a')
    saveDraft({ pickupNote: 'của A' })
    signIn('b')
    saveDraft({ deliveryNote: 'của B' })
    expect(JSON.parse(store.get('SWP_RHT_BOOKING_DRAFT_b')!).pickupNote).toBe('')
    expect(JSON.parse(store.get('SWP_RHT_BOOKING_DRAFT_a')!).pickupNote).toBe('của A')
  })

  it('gửi xong thì xóa bản nháp', async () => {
    const { clearDraft, saveDraft } = await import('./draft')
    signIn('a')
    saveDraft({ pickupNote: 'x' })
    clearDraft()
    expect(store.has('SWP_RHT_BOOKING_DRAFT_a')).toBe(false)
  })

  it('bản nháp hỏng thì bắt đầu lại từ đầu', async () => {
    const { saveDraft } = await import('./draft')
    signIn('a')
    store.set('SWP_RHT_BOOKING_DRAFT_a', '{hỏng')
    saveDraft({ pickupNote: 'mới' })
    expect(JSON.parse(store.get('SWP_RHT_BOOKING_DRAFT_a')!)).toMatchObject({ pickupNote: 'mới', horseIds: [] })
  })

  it('không ghi tệp hợp đồng vào sessionStorage', async () => {
    const { emptyHorse, saveDraft } = await import('./draft')
    signIn('a')
    saveDraft({ horseIds: [1], horses: {
      1: { ...emptyHorse(), coverage: 'PERSONAL', policyNumber: 'P1', policyFile: new File(['%PDF-1.7'], 'policy.pdf') },
    } })
    const raw = store.get('SWP_RHT_BOOKING_DRAFT_a')!
    expect(JSON.parse(raw).horses[1]).toMatchObject({ coverage: 'PERSONAL', policyNumber: 'P1' })
    expect(raw).not.toContain('policyFile')
  })
})
