// Khung trang CÔNG KHAI (giới thiệu): topbar, header marketing, footer, nút nổi. Chuyển từ index.html + home.css.
// Trang khách đã đăng nhập dùng khung gọn: CustomerLayout.tsx.
import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
import { CustomerLayout } from './CustomerLayout'
import s from './SiteChrome.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')

function useScrolled(y: number) {
  const [scrolled, setScrolled] = useState(false)
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > y)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [y])
  return scrolled
}

function Brand({ to }: { to: string }) {
  const { t } = useTranslation()
  return (
    <Link to={to} className={s.brand}>
      <img src="/logo.png" alt="" className={s.brandMark} />
      <span>{t('common.brandName')}</span>
    </Link>
  )
}

const navClass = ({ isActive }: { isActive: boolean }) => cx(s.navLink, isActive && s.active)

function PublicNav() {
  const { t } = useTranslation()
  return (
    <>
      <NavLink to="/" end className={navClass}>{t('nav.home')}</NavLink>
      <div className={s.drop}>
        <button type="button" className={s.dropBtn}>
          {t('nav.lookup')} <i className="fa-solid fa-chevron-down" aria-hidden="true" />
        </button>
        <div className={s.dropMenu}>
          <Link to="/?tab=order#tra-cuu">{t('nav.lookupOrder')}</Link>
          <Link to="/?tab=price#tra-cuu">{t('nav.pricing')}</Link>
        </div>
      </div>
      <Link to="/#mang-luoi" className={s.navLink}>{t('nav.network')}</Link>
      <Link to="/#dich-vu" className={s.navLink}>{t('nav.services')}</Link>
      <Link to="/#quy-trinh" className={s.navLink}>{t('nav.process')}</Link>
      <NavLink to="/terms" className={navClass}>{t('nav.terms')}</NavLink>
      <div className={s.actions}>
        <Link to="/login" className="btn btn-outline">{t('common.loginOrRegister')}</Link>
        <Link to="/login" className="btn btn-solid">{t('common.bookTrip')}</Link>
      </div>
    </>
  )
}

function Footer() {
  const { t } = useTranslation()
  return (
    <footer className={s.footer}>
      <div className={cx('wrap', s.footerGrid)}>
        <div>
          <Brand to="/" />
          <p>{t('common.footerDesc')}</p>
        </div>
        <div>
          <h4>{t('nav.lookup')}</h4>
          <Link to="/?tab=order#tra-cuu">{t('nav.lookupOrder')}</Link>
          <Link to="/?tab=price#tra-cuu">{t('nav.pricing')}</Link>
          <Link to="/#mang-luoi">{t('nav.network')}</Link>
        </div>
        <div>
          <h4>{t('nav.customer')}</h4>
          <Link to="/login">{t('common.login')}</Link>
          <Link to="/register">{t('common.register')}</Link>
          <Link to="/login">{t('common.bookTrip')}</Link>
        </div>
      </div>
      <div className={cx('wrap', s.copyright)}>{t('common.footerSub')}</div>
    </footer>
  )
}

export function PublicLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { pathname } = useLocation()
  const scrolled = useScrolled(10)
  const showTop = useScrolled(600)
  const [menuOpen, setMenuOpen] = useState(false)
  if ((pathname === '/terms' || pathname === '/payment/vnpay-return') && session?.role === 'customer') {
    return <CustomerLayout>{children}</CustomerLayout>
  }
  return (
    <>
      <div className={s.topbar}>
        <div className={cx('wrap', s.topbarInner)}>
          <span>{t('common.brandTagline')}</span>
        </div>
      </div>
      <header className={cx(s.header, scrolled && s.scrolled)}>
        <div className={cx('wrap', s.headerInner)}>
          <Brand to="/" />
          <button
            type="button"
            className={s.menuToggle}
            aria-label={menuOpen ? 'Đóng menu' : 'Mở menu'}
            aria-expanded={menuOpen}
            aria-controls="menu-chinh"
            onClick={() => setMenuOpen(o => !o)}
          ><i className={`fa-solid ${menuOpen ? 'fa-xmark' : 'fa-bars'}`} aria-hidden="true" /></button>
          <nav id="menu-chinh" className={cx(s.nav, menuOpen && s.open)} aria-label="Menu chính" onClick={() => setMenuOpen(false)}>
            <PublicNav />
          </nav>
        </div>
      </header>
      <main>{children}</main>
      <Footer />
      <button className={cx(s.floatTop, showTop && s.shown)} aria-label="Lên đầu trang" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}><i className="fa-solid fa-chevron-up" /></button>
      <TranslateToggle />
    </>
  )
}
