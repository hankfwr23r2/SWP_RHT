import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { ROLE_LABEL } from '@shared/auth/role'
import { Button } from '@shared/ui/Button'
import { Card } from '@shared/ui/Card'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import { ProfileHeader } from '@shared/ui/ProfileHeader'
import s from './StaffProfilePage.module.css'

export default function StaffProfilePage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const [changingPassword, setChangingPassword] = useState(false)

  if (!session) return null

  return (
    <div className={s.page}>
      <ProfileHeader name={session.name} detail={session.username} role={t(`roles.${session.role}`, ROLE_LABEL[session.role])}
        facts={[{ label: t('staffProfile.staffCode'), value: session.staffCode || t('staffProfile.noStaffCode') }]}>
        <Button variant="primary" icon={<i className="fa-solid fa-key" />} onClick={() => setChangingPassword(true)}>
          {t('staffProfile.changePassword')}
        </Button>
      </ProfileHeader>
      <Card title={t('staffProfile.accountInfo')}>
        <dl className={s.details}>
          <div><dt>{t('staffProfile.fullName')}</dt><dd>{session.name}</dd></div>
          <div><dt>{t('staffProfile.username')}</dt><dd>{session.username}</dd></div>
        </dl>
        <p className={s.note}>{t('staffProfile.managedByAdminNote')}</p>
      </Card>
      {changingPassword && <ChangePasswordModal onClose={() => setChangingPassword(false)} />}
    </div>
  )
}
