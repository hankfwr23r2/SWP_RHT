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

export function AuthShell({ title, subtitle, heading = 'Vận chuyển Ngựa', tagline = 'Đặt chuyến, theo dõi đơn và thanh toán mọi nơi.', homeHref = '/', homeExternal, back, children }: AuthShellProps) {
  return (
    <div className={s.layout}>
      <div className={s.left}>
        {homeExternal
          ? <a href={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />Vận chuyển Ngựa đua</a>
          : <Link to={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />Vận chuyển Ngựa đua</Link>}
        <div className={s.content}>
          <p className={s.heading}>{heading}</p>
          <p className={s.tagline}>{tagline}</p>
        </div>
        <div className={s.footer}>Hệ thống vận chuyển ngựa</div>
      </div>
      <div className={s.right}>
        <div className={s.box}>
          {back && <div className={s.back}>{back}</div>}
          <h1>{title}</h1>
          <p>{subtitle}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

export const authStyles = s
