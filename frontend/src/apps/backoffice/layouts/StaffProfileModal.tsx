import { useState } from 'react'
import { ChangePasswordModal } from '@shared/ui/ChangePasswordModal'
import { Modal } from '@shared/ui/Modal'
import { ROLE_LABEL } from '@shared/types/role'
import type { Session } from '@shared/types/role'

export function StaffProfileModal({ session, onClose }: { session: Session; onClose: () => void }) {
  const [changingPassword, setChangingPassword] = useState(false)

  if (changingPassword) {
    return <ChangePasswordModal onClose={() => setChangingPassword(false)} />
  }

  return (
    <Modal
      title="Hồ sơ nhân viên"
      subtitle="Thông tin tài khoản của bạn"
      onClose={onClose}
      footer={
        <button type="button" className="btn btn-primary" onClick={() => setChangingPassword(true)}>
          <i className="fa-solid fa-key" /> Đổi mật khẩu
        </button>
      }
    >
      <dl style={{ display: 'grid', gridTemplateColumns: 'minmax(110px, 0.7fr) 1.3fr', gap: '14px 18px', margin: 0 }}>
        <dt style={{ color: 'var(--muted)' }}>Họ và tên</dt>
        <dd style={{ margin: 0, fontWeight: 600 }}>{session.name}</dd>
        <dt style={{ color: 'var(--muted)' }}>Email / tên đăng nhập</dt>
        <dd style={{ margin: 0, overflowWrap: 'anywhere' }}>{session.username}</dd>
        <dt style={{ color: 'var(--muted)' }}>Vai trò</dt>
        <dd style={{ margin: 0 }}>{ROLE_LABEL[session.role]}</dd>
      </dl>
    </Modal>
  )
}
