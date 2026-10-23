import { useNavigation } from 'react-router'

// Thanh tiến trình trên cùng khi router đang tải dữ liệu/file của trang sắp mở (trang cũ vẫn hiện tới lúc đó)
export function NavigationBar() {
  return <div className="nav-progress" role="progressbar" aria-label="Đang tải trang" />
}

export function NavigationProgress() {
  return useNavigation().state === 'loading' ? <NavigationBar /> : null
}
