import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Modal } from './Modal'
import { useToast } from './toast'
import { authApi } from '../api/auth'
import s from './ChangePasswordModal.module.css'

interface ChangePasswordModalProps {
  onClose: () => void
}

export function ChangePasswordModal({ onClose }: ChangePasswordModalProps) {
  const { t } = useTranslation()
  const toast = useToast()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [showCurrent, setShowCurrent] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)

    if (!currentPassword) {
      setError(t('changePassword.errorCurrentRequired', 'Vui lòng nhập mật khẩu hiện tại'))
      return
    }
    if (!newPassword) {
      setError(t('changePassword.errorNewRequired', 'Vui lòng nhập mật khẩu mới'))
      return
    }
    if (newPassword.length < 6) {
      setError(t('changePassword.errorMinLength', 'Mật khẩu mới phải có tối thiểu 6 ký tự'))
      return
    }
    if (newPassword === currentPassword) {
      setError(t('changePassword.errorSameAsCurrent', 'Mật khẩu mới không được trùng với mật khẩu hiện tại'))
      return
    }
    if (newPassword !== confirmPassword) {
      setError(t('changePassword.errorConfirmMismatch', 'Xác nhận mật khẩu mới không trùng khớp'))
      return
    }

    setLoading(true)
    try {
      await authApi.changePassword({
        currentPassword,
        newPassword,
        confirmPassword,
      })
      toast(t('changePassword.successToast', 'Đổi mật khẩu thành công!'), 'success')
      onClose()
    } catch (err) {
      const msg = err instanceof Error ? err.message : t('changePassword.defaultError', 'Có lỗi xảy ra khi đổi mật khẩu')
      setError(msg)
      toast(msg, 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Modal
      title={t('changePassword.modalTitle', 'Đổi mật khẩu tài khoản')}
      subtitle={t('changePassword.modalSubtitle', 'Cập nhật mật khẩu bảo mật mới cho tài khoản của bạn')}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-ghost" onClick={onClose} disabled={loading}>
            {t('changePassword.cancel', 'Hủy')}
          </button>
          <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={loading}>
            {loading ? (
              <>
                <i className="fa-solid fa-spinner fa-spin" /> {t('changePassword.updating', 'Đang cập nhật...')}
              </>
            ) : (
              <>
                <i className="fa-solid fa-check" /> {t('changePassword.save', 'Lưu mật khẩu')}
              </>
            )}
          </button>
        </>
      }
    >
      <form onSubmit={handleSubmit} className={s.form}>
        {error && (
          <div className={s.error}>
            <i className="fa-solid fa-circle-exclamation" />
            <span>{error}</span>
          </div>
        )}

        <div className={`form-group ${s.group}`}>
          <label htmlFor="cp-current">{t('changePassword.currentPassword', 'Mật khẩu hiện tại *')}</label>
          <div className={s.field}>
            <input
              id="cp-current"
              type={showCurrent ? 'text' : 'password'}
              className={`form-control ${s.input}`}
              value={currentPassword}
              onChange={e => setCurrentPassword(e.target.value)}
              placeholder={t('changePassword.currentPasswordPlaceholder', 'Nhập mật khẩu đang dùng')}
              autoComplete="current-password"
              disabled={loading}
            />
            <button
              type="button"
              onClick={() => setShowCurrent(!showCurrent)}
              className={s.toggle}
              tabIndex={-1}
            >
              <i className={`fa-regular ${showCurrent ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
        </div>

        <div className={`form-group ${s.group}`}>
          <label htmlFor="cp-new">{t('changePassword.newPassword', 'Mật khẩu mới *')}</label>
          <div className={s.field}>
            <input
              id="cp-new"
              type={showNew ? 'text' : 'password'}
              className={`form-control ${s.input}`}
              value={newPassword}
              onChange={e => setNewPassword(e.target.value)}
              placeholder={t('changePassword.newPasswordPlaceholder', 'Ít nhất 6 ký tự')}
              autoComplete="new-password"
              disabled={loading}
            />
            <button
              type="button"
              onClick={() => setShowNew(!showNew)}
              className={s.toggle}
              tabIndex={-1}
            >
              <i className={`fa-regular ${showNew ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
        </div>

        <div className={`form-group ${s.group}`}>
          <label htmlFor="cp-confirm">{t('changePassword.confirmPassword', 'Xác nhận mật khẩu mới *')}</label>
          <div className={s.field}>
            <input
              id="cp-confirm"
              type={showConfirm ? 'text' : 'password'}
              className={`form-control ${s.input}`}
              value={confirmPassword}
              onChange={e => setConfirmPassword(e.target.value)}
              placeholder={t('changePassword.confirmPasswordPlaceholder', 'Nhập lại mật khẩu mới')}
              autoComplete="new-password"
              disabled={loading}
            />
            <button
              type="button"
              onClick={() => setShowConfirm(!showConfirm)}
              className={s.toggle}
              tabIndex={-1}
            >
              <i className={`fa-regular ${showConfirm ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
        </div>
      </form>
    </Modal>
  )
}
