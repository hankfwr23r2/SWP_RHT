// Khung trang đăng nhập / đăng ký dùng chung cho khách và nhân viên.
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
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

export function AuthShell({ title, subtitle, heading, tagline, homeHref = '/', homeExternal, back, children }: AuthShellProps) {
  const { t } = useTranslation()
  const displayHeading = heading ?? t('common.brandName')
  const displayTagline = tagline ?? t('auth.tagline')

  return (
    <div className={s.layout}>
      <div className={s.left}>
        {homeExternal
          ? <a href={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />{t('common.brandName')}</a>
          : <Link to={homeHref} className={s.brand}><img src="/logo.png" alt="" className={s.brandMark} />{t('common.brandName')}</Link>}
        <div className={s.content}>
          <p className={s.heading}>{displayHeading}</p>
          <p className={s.tagline}>{displayTagline}</p>
        </div>
        <div className={s.footer}>{t('common.footerSub')}</div>
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
