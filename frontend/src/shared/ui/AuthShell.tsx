// Khung trang đăng nhập / đăng ký dùng chung cho khách và nhân viên.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import s from './AuthShell.module.css'

interface AuthShellProps {
  title: string
  subtitle: string
  heading?: string
  tagline?: string
  homeHref?: string
  homeExternal?: boolean // true: link ra app khác (vd. từ app nội bộ về trang chủ khách)
  back?: ReactNode // liên kết đặt trên cùng khung form (vd. quay lại Trang chủ)
  children: ReactNode
}

export function AuthShell({ title, subtitle, heading = 'Vận chuyển Ngựa đua Chuyên nghiệp', tagline = 'An toàn, chuẩn quốc tế và giám sát 24/7 trên mọi dặm đường của chiến mã.', homeHref = '/', homeExternal, back, children }: AuthShellProps) {
  return (
    <div className={s.layout}>
      <div className={s.left}>
        {homeExternal
          ? <a href={homeHref} className={s.brand}><span className={s.brandMark}>EQ</span>Vận chuyển Ngựa</a>
          : <Link to={homeHref} className={s.brand}><span className={s.brandMark}>EQ</span>Vận chuyển Ngựa</Link>}
        <div className={s.content}>
          <h1>{heading}</h1>
          <p>{tagline}</p>
        </div>
        <div className={s.footer}>© 2026 Vận chuyển Ngựa đua. SWP Project.</div>
      </div>
      <div className={s.right}>
        <div className={s.box}>
          {back && <div className={s.back}>{back}</div>}
          <h2>{title}</h2>
          <p>{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

export const authStyles = s
