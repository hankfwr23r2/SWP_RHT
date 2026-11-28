// Tải trước file của một trang khi người dùng sắp mở (rê chuột, focus), để bấm vào là hiện ngay.
const pages = new Map<string, () => Promise<unknown>>()

export function registerPreload(path: string, load: () => Promise<unknown>) {
  pages.set(path, load)
}

// Lỗi mạng lúc tải trước thì bỏ qua: bấm vào trang sẽ tải lại và báo lỗi đúng chỗ
export function preloadRoute(path: string) {
  pages.get(path.split(/[?#]/)[0])?.().catch(() => {})
}
