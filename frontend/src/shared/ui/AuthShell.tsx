// Khung trang đăng nhập / đăng ký dùng chung cho khách và nhân viên.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import s from './AuthShell.module.css'

// Những điều khách nhận được, đúng với quy trình hệ thống đang chạy. Trang nhân viên truyền points={[]}.
const CUSTOMER_POINTS: [icon: string, text: string][] = [
  ['fa-route', 'Tuyến nội địa Việt Nam và sang Lào, Campuchia qua cửa khẩu đường bộ'],
  ['fa-file-medical', 'Chuyên viên xác minh hồ sơ ngựa trước mỗi chuyến'],
  ['fa-file-invoice-dollar', 'Báo giá được duyệt trước khi bạn đặt cọc 30%'],
]

interface AuthShellProps {
  title: string
  subtitle: string
  heading?: string
  tagline?: string
  points?: [icon: string, text: string][]
  homeHref?: string
  homeExternal?: boolean // true: link ra app khác (vd. từ app nội bộ về trang chủ khách)
  back?: ReactNode // liên kết đặt trên cùng khung form (vd. quay lại Trang chủ)
  children: ReactNode
}

export function AuthShell({ title, subtitle, heading = 'Mỗi chuyến ngựa có người chịu trách nhiệm từng khâu', tagline = 'Đặt chuyến, theo dõi đơn và thanh toán ở một nơi.', points = CUSTOMER_POINTS, homeHref = '/', homeExternal, back, children }: AuthShellProps) {
  return (
    <div className={s.layout}>
      <div className={s.left}>
        {homeExternal
          ? <a href={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />Vận chuyển Ngựa đua</a>
          : <Link to={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />Vận chuyển Ngựa đua</Link>}
        <div className={s.content}>
          <img src="/images/trailers.jpg" alt="Các xe chở ngựa đỗ trong bãi" className={s.photo} />
          <p className={s.heading}>{heading}</p>
          <p className={s.tagline}>{tagline}</p>
          {points.length > 0 && (
            <ul className={s.points}>
              {points.map(([icon, text]) => <li key={text}><i className={`fa-solid ${icon}`} aria-hidden="true" />{text}</li>)}
            </ul>
          )}
        </div>
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
