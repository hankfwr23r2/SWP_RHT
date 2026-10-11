// Khung GỌN cho khách đã đăng nhập: header 64px, hotline nhỏ, footer mỏng.
// Cùng màu/font/nút với trang chủ, nhưng bỏ topbar, header marketing và footer lớn để tập trung vào công việc.
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { HOTLINE } from '@shared/config/business-rules'
import { PageTransition } from '@shared/motion/motion'
import { AppHeader, appHeaderStyles as h } from '@shared/ui/AppHeader'
import { TranslateToggle } from '@shared/ui/TranslateToggle'

const MENU: [string, string][] = [
  ['/portal', 'Trang chủ'],
  ['/horses', 'Hồ sơ ngựa'],
  ['/booking/route', 'Đặt chuyến mới'],
  ['/orders', 'Đơn của tôi'],
  ['/history', 'Lịch sử đơn'],
]

const isActive = (to: string, path: string) =>
  path === to || (to === '/booking/route' && path.startsWith('/booking')) || (to === '/orders' && path.startsWith('/orders/'))

export function CustomerLayout({ children }: { children: ReactNode }) {
  const { session, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <>
      <AppHeader
        homeHref="/portal" contained links={MENU} isActive={isActive} userName={session?.name}
        onLogout={() => { logout(); navigate('/') }}
        extra={<a href={`tel:${HOTLINE.replace(/\s/g, '')}`} className={h.hotline}><i className="fa-solid fa-headset" /> <strong>{HOTLINE}</strong></a>}
      />
      <main><PageTransition>{children}</PageTransition></main>
      <footer className={h.footer}>© 2026 Vận chuyển Ngựa đua · Việt Nam · Lào · Campuchia · Hotline {HOTLINE}</footer>
      <TranslateToggle />
    </>
  )
}
