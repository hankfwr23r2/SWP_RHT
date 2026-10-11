// Khung trang CÔNG KHAI (giới thiệu): topbar, header marketing, footer, nút nổi. Chuyển từ index.html + home.css.
// Trang khách đã đăng nhập dùng khung gọn: CustomerLayout.tsx.
import { useEffect, useState, type ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { HOTLINE } from '@shared/config/business-rules'
import { TranslateToggle } from '@shared/ui/TranslateToggle'
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
  return <Link to={to} className={s.brand}><span className={s.brandMark}>EQ</span><span>Vận chuyển Ngựa</span></Link>
}

const navClass = ({ isActive }: { isActive: boolean }) => cx(s.navLink, isActive && s.active)

function PublicNav() {
  return (
    <>
      <NavLink to="/" end className={navClass}>Trang chủ</NavLink>
      <div className={s.drop}>
        <button className={s.dropBtn}>Tra cứu <i className="fa-solid fa-chevron-down" /></button>
        <div className={s.dropMenu}>
          <Link to="/?tab=order#tra-cuu">Tra cứu đơn hàng</Link>
          
          <Link to="/?tab=price#tra-cuu">Bảng giá</Link>
        </div>
      </div>
      <Link to="/#mang-luoi" className={s.navLink}>Mạng lưới</Link>
      <Link to="/#dich-vu" className={s.navLink}>Dịch vụ</Link>
      <Link to="/#quy-trinh" className={s.navLink}>Quy trình</Link>
      <div className={s.actions}>
        <Link to="/login" className="btn btn-outline">Đặt chuyến</Link>
        <Link to="/login" className="btn btn-solid">Đăng nhập / Đăng ký</Link>
      </div>
    </>
  )
}

function Footer() {
  return (
    <footer className={s.footer}>
      <div className={cx('wrap', s.footerGrid)}>
        <div>
          <Brand to="/" />
          <p>Vận chuyển ngựa đua đường bộ trong Việt Nam và giữa Việt Nam với Lào, Campuchia.</p>
        </div>
        <div>
          <h4>Tra cứu</h4>
          <Link to="/?tab=order#tra-cuu">Tra cứu đơn hàng</Link>
          
          <Link to="/?tab=price#tra-cuu">Bảng giá</Link>
          <Link to="/#mang-luoi">Mạng lưới</Link>
        </div>
        <div>
          <h4>Khách hàng</h4>
          <Link to="/login">Đăng nhập</Link>
          <Link to="/register">Đăng ký</Link>
          <Link to="/login">Đặt chuyến</Link>
        </div>
        <div>
          <h4>Liên hệ</h4>
          <p><i className="fa-solid fa-phone" /> {HOTLINE} (24/7)</p>
        </div>
      </div>
      <div className={cx('wrap', s.copyright)}>© 2026 Vận chuyển Ngựa đua. SWP Project.</div>
    </footer>
  )
}

export function PublicLayout({ children }: { children: ReactNode }) {
  const scrolled = useScrolled(10)
  const showTop = useScrolled(600)
  const [menuOpen, setMenuOpen] = useState(false)
  return (
    <>
      <div className={s.topbar}>
        <div className={cx('wrap', s.topbarInner)}>
          <span>Vận chuyển ngựa đua đường bộ · Việt Nam · Lào · Campuchia</span>
          <span><i className="fa-solid fa-phone" /> Hotline <strong>{HOTLINE}</strong> (24/7)</span>
        </div>
      </div>
      <header className={cx(s.header, scrolled && s.scrolled)}>
        <div className={cx('wrap', s.headerInner)}>
          <Brand to="/" />
          <button className={s.menuToggle} aria-label="Mở menu" onClick={() => setMenuOpen(o => !o)}><i className="fa-solid fa-bars" /></button>
          <nav className={cx(s.nav, menuOpen && s.open)} aria-label="Menu chính" onClick={() => setMenuOpen(false)}>
            <PublicNav />
          </nav>
        </div>
      </header>
      <main>{children}</main>
      <Footer />
      <a href={`tel:${HOTLINE.replace(/\s/g, '')}`} className={s.floatCall} aria-label={`Gọi hotline ${HOTLINE}`}><i className="fa-solid fa-phone" /></a>
      <button className={cx(s.floatTop, showTop && s.shown)} aria-label="Lên đầu trang" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}><i className="fa-solid fa-chevron-up" /></button>
      <TranslateToggle />
    </>
  )
}

