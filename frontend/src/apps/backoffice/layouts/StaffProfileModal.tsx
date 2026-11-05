import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import { Modal } from '@shared/ui/Modal'
import { ROLE_LABEL, type Session } from '@shared/types/role'

export function StaffProfileModal({ session, onClose }: { session: Session; onClose: () => void }) {
  const { t } = useTranslation()
  const [changingPassword, setChangingPassword] = useState(false)

  if (changingPassword) return <ChangePasswordModal onClose={() => setChangingPassword(false)} />

  return (
    <Modal
      title={t('staffProfile.modalTitle', 'Hồ sơ nhân viên')}
      subtitle={t('staffProfile.modalSubtitle', 'Thông tin tài khoản của bạn')}
      onClose={onClose}
      footer={
        <button type="button" className="btn btn-primary" onClick={() => setChangingPassword(true)}>
          <i className="fa-solid fa-key" /> {t('staffProfile.changePassword', 'Đổi mật khẩu')}
        </button>
      }
    >
      <dl style={{ display: 'grid', gridTemplateColumns: 'minmax(110px, 0.7fr) 1.3fr', gap: '14px 18px', margin: 0 }}>
        <dt style={{ color: 'var(--muted)' }}>{t('staffProfile.fullName', 'Họ và tên')}</dt>
        <dd style={{ margin: 0, fontWeight: 600 }}>{session.name}</dd>
        <dt style={{ color: 'var(--muted)' }}>{t('staffProfile.username', 'Email / tên đăng nhập')}</dt>
        <dd style={{ margin: 0, overflowWrap: 'anywhere' }}>{session.username}</dd>
        <dt style={{ color: 'var(--muted)' }}>{t('staffProfile.staffCode', 'Mã nhân viên')}</dt>
        <dd style={{ margin: 0 }}>{session.staffCode || t('staffProfile.noStaffCode', 'Chưa có mã nhân viên')}</dd>
        <dt style={{ color: 'var(--muted)' }}>{t('staffProfile.role', 'Vai trò')}</dt>
        <dd style={{ margin: 0 }}>{t(`roles.${session.role}`, ROLE_LABEL[session.role])}</dd>
      </dl>
    </Modal>
  )
}
