// Xóa dữ liệu mock còn sót lại từ các phiên bản frontend cũ.
export function clearMockData(storage: Pick<Storage, 'length' | 'key' | 'removeItem'>) {
  const keys: string[] = []
  for (let i = 0; i < storage.length; i++) { const k = storage.key(i); if (k?.startsWith('SWP_MOCK_')) keys.push(k) }
  keys.forEach(k => storage.removeItem(k))
}
if (typeof sessionStorage !== 'undefined') {
  try {
    clearMockData(sessionStorage)
  } catch { /* bỏ qua khi bị chặn lưu trữ */ }
}

const isTestEnv = import.meta.env?.MODE === 'test'

export function createStore<T extends { id: string }>(_key: string, seed: () => T[]) {
  let items = isTestEnv ? seed() : []

  return {
    all: () => items,
    get: (id: string) => items.find(x => x.id === id),
    update(id: string, patch: Partial<T>) {
      const i = items.findIndex(x => x.id === id)
      if (i < 0) throw new Error(`Không tìm thấy ${id}`)
      items[i] = { ...items[i], ...patch }
      return items[i]
    },
    add(item: T) {
      items.push(item)
      return item
    },
    remove(id: string) {
      items = items.filter(x => x.id !== id)
    },
    reset() { items = isTestEnv ? seed() : [] },
  }
}
