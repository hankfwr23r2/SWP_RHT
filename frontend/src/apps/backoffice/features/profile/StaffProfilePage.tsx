import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { ROLE_LABEL } from '@shared/types/role'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import s from './StaffProfilePage.module.css'

export default function StaffProfilePage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const [changingPassword, setChangingPassword] = useState(false)

  if (!session) return null

  return (
    <div className={s.page}>
      <header className={s.heading}>
        <div>
          <h1>{t('staffProfile.pageTitle', 'Hồ sơ nhân viên')}</h1>
          <p>{t('staffProfile.pageSubtitle', 'Thông tin tài khoản và bảo mật của bạn.')}</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setChangingPassword(true)}>
          <i className="fa-solid fa-key" /> {t('staffProfile.changePassword', 'Đổi mật khẩu')}
        </button>
      </header>

      <section className={s.card} aria-labelledby="profile-info-title">
        <h2 id="profile-info-title">{t('staffProfile.accountInfo', 'Thông tin tài khoản')}</h2>
        <dl className={s.details}>
          <div><dt>{t('staffProfile.fullName', 'Họ và tên')}</dt><dd>{session.name}</dd></div>
          <div><dt>{t('staffProfile.username', 'Email / tên đăng nhập')}</dt><dd>{session.username}</dd></div>
          <div><dt>{t('staffProfile.staffCode', 'Mã nhân viên')}</dt><dd>{session.staffCode || t('staffProfile.noStaffCode', 'Chưa có mã nhân viên')}</dd></div>
          <div><dt>{t('staffProfile.role', 'Vai trò')}</dt><dd>{t(`roles.${session.role}`, ROLE_LABEL[session.role])}</dd></div>
        </dl>
        <p className={s.note}>{t('staffProfile.managedByAdminNote', 'Thông tin tài khoản do quản trị viên quản lý. Bạn có thể tự thay đổi mật khẩu.')}</p>
      </section>

      {changingPassword && <ChangePasswordModal onClose={() => setChangingPassword(false)} />}
    </div>
  )
}
