// Kho dữ liệu mẫu trong bộ nhớ, lưu sessionStorage để đi trọn luồng giữa các trang trong cùng tab.
// Khi có Spring Boot: xóa file này, các service gọi fetch('/api/...') thay vì đọc/ghi store.

// Tăng số này mỗi khi sửa dữ liệu mẫu (mock/*) để trình duyệt bỏ bản cũ đã lưu trong phiên và nạp bản mới.
export const MOCK_VERSION = 22

// Dữ liệu mẫu lưu trong phiên để đi trọn luồng giữa hai app (khách và nội bộ là hai trang riêng, chuyển qua lại không mất dữ liệu).
// Bấm F5 (tải lại trang) thì đặt lại dữ liệu mẫu về ban đầu, để thử lại từ đầu; phiên đăng nhập và bản nháp đặt chuyến được giữ.
export function clearMockData(storage: Pick<Storage, 'length' | 'key' | 'removeItem'>) {
  const keys: string[] = []
  for (let i = 0; i < storage.length; i++) { const k = storage.key(i); if (k?.startsWith('SWP_MOCK_')) keys.push(k) }
  keys.forEach(k => storage.removeItem(k))
}
const isReload = () => {
  try { return (performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined)?.type === 'reload' } catch { return false }
}
if (typeof sessionStorage !== 'undefined' && isReload()) { try { clearMockData(sessionStorage) } catch { /* bỏ qua khi bị chặn lưu trữ */ } }

export function createStore<T extends { id: string }>(key: string, seed: () => T[]) {
  const storageKey = `SWP_MOCK_v${MOCK_VERSION}_${key}`
  let items: T[] | null = null

  const load = (): T[] => {
    if (items) return items
    try {
      const raw = sessionStorage.getItem(storageKey)
      items = raw ? JSON.parse(raw) : seed()
    } catch {
      items = seed()
    }
    return items!
  }
  const persist = () => {
    try { sessionStorage.setItem(storageKey, JSON.stringify(items)) } catch { /* bỏ qua khi bị chặn lưu trữ */ }
  }

  return {
    all: () => load(),
    get: (id: string) => load().find(x => x.id === id),
    update(id: string, patch: Partial<T>) {
      const list = load()
      const i = list.findIndex(x => x.id === id)
      if (i < 0) throw new Error(`Không tìm thấy ${id}`)
      list[i] = { ...list[i], ...patch }
      persist()
      return list[i]
    },
    add(item: T) {
      load().push(item)
      persist()
      return item
    },
    remove(id: string) {
      items = load().filter(x => x.id !== id)
      persist()
    },
    reset() { items = seed(); persist() },
  }
}
