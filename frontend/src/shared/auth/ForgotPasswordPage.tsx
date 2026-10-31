import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { isValidEmail } from '@shared/lib/contactValidation'

function ForgotPasswordFlow({ staffMode }: { staffMode: boolean }) {
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [requested, setRequested] = useState(false)
  const [completed, setCompleted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [errorField, setErrorField] = useState<string | null>(null)
  const [showPwd, setShowPwd] = useState(false)

  // Báo lỗi gắn với một ô: viền đỏ, aria-invalid và đưa con trỏ về ô đó
  const fail = (message: string, fieldId: string) => {
    setError(message)
    setErrorField(fieldId)
    document.getElementById(fieldId)?.focus()
  }
  const fieldState = (fieldId: string) => ({
    className: `form-control${errorField === fieldId ? ' invalid' : ''}`,
    'aria-invalid': errorField === fieldId || undefined,
    'aria-describedby': errorField === fieldId ? 'reset-error' : undefined,
  })
  const clearError = () => { setError(''); setErrorField(null) }

  const sendOtp = async () => {
    setLoading(true)
    setError('')
    try {
      setMessage(await authApi.requestPasswordResetOtp(email.trim()))
      setRequested(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi được mã OTP. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const requestOtp = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!email.trim()) return fail('Vui lòng nhập email.', 'reset-email')
    if (!isValidEmail(email)) return fail('Email không đúng định dạng. Ví dụ: ten@example.com.', 'reset-email')
    void sendOtp()
  }

  const resetPassword = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    clearError()
    if (otp.length !== 6) return fail('Mã OTP gồm 6 chữ số.', 'reset-otp')
    if (newPassword.length < 6) return fail('Mật khẩu mới cần có ít nhất 6 ký tự.', 'new-password')
    if (newPassword !== confirmPassword) return fail('Mật khẩu xác nhận không khớp.', 'confirm-password')

    setLoading(true)
    try {
      setMessage(await authApi.resetPasswordWithOtp({ email: email.trim(), otp: otp.trim(), newPassword, confirmPassword }))
      setCompleted(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không đặt lại được mật khẩu. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const title = staffMode ? 'Khôi phục mật khẩu nhân viên' : 'Quên mật khẩu'
  const back = <Link to="/login"><i className="fa-solid fa-arrow-left" /> Quay lại đăng nhập</Link>
  const useOtherEmail = () => {
    setRequested(false)
    setOtp('')
    setMessage('')
    clearError()
  }

  return (
    <AuthShell title={title} subtitle="Nhận mã OTP qua email để tạo mật khẩu mới." heading="Khôi phục tài khoản" points={staffMode ? [] : undefined} back={back} homeExternal={staffMode}>
      {error && <div id="reset-error" className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
      {message && !error && <div className={`alert alert-success ${s.error}`} role="status"><i className="fa-solid fa-circle-check" /><div>{message}</div></div>}

      {!requested && (
        <form onSubmit={requestOtp} noValidate>
          <div className="form-group">
            <label htmlFor="reset-email">Email tài khoản</label>
            <input id="reset-email" {...fieldState('reset-email')} type="email" autoComplete="email" value={email} onChange={e => { setEmail(e.target.value); clearError() }} placeholder="ten@example.com" />
          </div>
          <button className="btn btn-primary btn-full btn-lg" type="submit" disabled={loading}>
            {loading ? 'Đang gửi mã...' : 'Gửi mã OTP'}
          </button>
        </form>
      )}

      {requested && !completed && (
        <form onSubmit={resetPassword} noValidate>
          <div className="form-group">
            <label htmlFor="reset-email-display">Email tài khoản</label>
            <input id="reset-email-display" className="form-control" type="email" value={email} readOnly />
            <p className="form-hint"><button type="button" className={s.linkButton} onClick={useOtherEmail}>Dùng email khác</button></p>
          </div>
          <div className="form-group">
            <label htmlFor="reset-otp">Mã OTP</label>
            <input id="reset-otp" {...fieldState('reset-otp')} inputMode="numeric" autoComplete="one-time-code" maxLength={6} value={otp} onChange={e => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); clearError() }} placeholder="Nhập mã 6 chữ số" />
          </div>
          <div className="form-group">
            <label htmlFor="new-password">Mật khẩu mới</label>
            <div className={s.pwdWrap}>
              <input id="new-password" {...fieldState('new-password')} type={showPwd ? 'text' : 'password'} autoComplete="new-password" maxLength={100} value={newPassword} onChange={e => { setNewPassword(e.target.value); clearError() }} />
              <button type="button" className={s.pwdToggle} onClick={() => setShowPwd(v => !v)} aria-label={showPwd ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
                <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
              </button>
            </div>
            <p className="form-hint">Tối thiểu 6 ký tự, khác mật khẩu hiện tại.</p>
          </div>
          <div className="form-group">
            <label htmlFor="confirm-password">Nhập lại mật khẩu mới</label>
            <input id="confirm-password" {...fieldState('confirm-password')} type={showPwd ? 'text' : 'password'} autoComplete="new-password" maxLength={100} value={confirmPassword} onChange={e => { setConfirmPassword(e.target.value); clearError() }} />
          </div>
          <button className="btn btn-primary btn-full btn-lg" type="submit" disabled={loading}>
            {loading ? 'Đang cập nhật...' : 'Xác nhận mật khẩu mới'}
          </button>
          <button className="btn btn-ghost btn-full btn-lg" type="button" disabled={loading} onClick={() => void sendOtp()} style={{ marginTop: 10 }}>
            Gửi lại mã OTP
          </button>
        </form>
      )}

      {completed && <Link className="btn btn-primary btn-full btn-lg" to="/login">Đến trang đăng nhập</Link>}
    </AuthShell>
  )
}

export function CustomerForgotPasswordPage() {
  return <ForgotPasswordFlow staffMode={false} />
}

export function StaffForgotPasswordPage() {
  return <ForgotPasswordFlow staffMode />
}
