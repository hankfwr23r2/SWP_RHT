import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@shared/ui/Button'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import { Modal } from '@shared/ui/Modal'
import { ROLE_LABEL, type Session } from '@shared/types/role'
import s from './StaffProfileModal.module.css'

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
        <Button variant="primary" icon={<i className="fa-solid fa-key" />} onClick={() => setChangingPassword(true)}>
          {t('staffProfile.changePassword', 'Đổi mật khẩu')}
        </Button>
      }
    >
      <dl className={s.details}>
        <dt>{t('staffProfile.fullName', 'Họ và tên')}</dt>
        <dd className={s.strong}>{session.name}</dd>
        <dt>{t('staffProfile.username', 'Email / tên đăng nhập')}</dt>
        <dd className={s.wrap}>{session.username}</dd>
        <dt>{t('staffProfile.staffCode', 'Mã nhân viên')}</dt>
        <dd>{session.staffCode || t('staffProfile.noStaffCode', 'Chưa có mã nhân viên')}</dd>
        <dt>{t('staffProfile.role', 'Vai trò')}</dt>
        <dd>{t(`roles.${session.role}`, ROLE_LABEL[session.role])}</dd>
      </dl>
    </Modal>
  )
}
