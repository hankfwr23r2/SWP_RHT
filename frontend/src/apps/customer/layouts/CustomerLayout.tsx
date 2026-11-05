// Khung GỌN cho khách đã đăng nhập: header 64px, hotline nhỏ, footer mỏng.
// Cùng màu/font/nút với trang chủ, nhưng bỏ topbar, header marketing và footer lớn để tập trung vào công việc.
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { PageTransition } from '@shared/motion/motion'
import { AppHeader, appHeaderStyles as h } from '@shared/ui/AppHeader'
import { NoticeBell } from '@shared/ui/NoticeBell'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import './customer-theme.css'

const isActive = (to: string, path: string) =>
  path === to || (to === '/booking/route' && path.startsWith('/booking')) || (to === '/orders' && path.startsWith('/orders/'))

export function CustomerLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { session, logout } = useAuth()
  const navigate = useNavigate()

  const menu: [string, string][] = [
    ['/portal', t('nav.home')],
    ['/horses', t('nav.horses')],
    ['/booking/route', t('common.bookNewTrip')],
    ['/orders', t('nav.myOrders')],
    ['/history', t('nav.orderHistory')],
    ['/terms', t('nav.terms')],
  ]

  return (
    <>
      <AppHeader
        homeHref="/portal" contained links={menu} isActive={isActive} userName={session?.name} profileHref="/profile"
        onLogout={() => { logout(); navigate('/') }}
        extra={
          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <NoticeBell />
          </div>
        }
      />
      <main className="customer-theme"><PageTransition>{children}</PageTransition></main>
      <footer className={h.footer}>{t('common.footerCopyright')}</footer>
      <TranslateToggle />
    </>
  )
}
