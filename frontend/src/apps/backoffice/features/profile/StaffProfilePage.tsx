import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { ROLE_LABEL } from '@shared/types/role'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import s from './StaffProfilePage.module.css'

export default function StaffProfilePage() {
  const { session } = useAuth()
  const [changingPassword, setChangingPassword] = useState(false)

  if (!session) return null

  return (
    <div className={s.page}>
      <header className={s.heading}>
        <div>
          <h1>Hồ sơ nhân viên</h1>
          <p>Thông tin tài khoản và bảo mật của bạn.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setChangingPassword(true)}>
          <i className="fa-solid fa-key" /> Đổi mật khẩu
        </button>
      </header>

      <section className={s.card} aria-labelledby="profile-info-title">
        <h2 id="profile-info-title">Thông tin tài khoản</h2>
        <dl className={s.details}>
          <div><dt>Họ và tên</dt><dd>{session.name}</dd></div>
          <div><dt>Email / tên đăng nhập</dt><dd>{session.username}</dd></div>
          <div><dt>Mã nhân viên</dt><dd>{session.staffCode || 'Chưa có mã nhân viên'}</dd></div>
          <div><dt>Vai trò</dt><dd>{ROLE_LABEL[session.role]}</dd></div>
        </dl>
        <p className={s.note}>Thông tin tài khoản do quản trị viên quản lý. Bạn có thể tự thay đổi mật khẩu.</p>
      </section>

      {changingPassword && <ChangePasswordModal onClose={() => setChangingPassword(false)} />}
    </div>
  )
}
