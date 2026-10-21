import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'

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
    void sendOtp()
  }

  const resetPassword = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError('')
    if (newPassword.length < 6) {
      setError('Mật khẩu mới cần có ít nhất 6 ký tự.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Mật khẩu xác nhận không khớp.')
      return
    }

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

  return (
    <AuthShell title={title} subtitle="Nhận mã OTP qua email để tạo mật khẩu mới." heading="Khôi phục tài khoản" back={back} homeHref={staffMode ? '/' : '/'} homeExternal={staffMode}>
      {error && <div className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
      {message && !error && <div className="alert alert-success" role="status">{message}</div>}

      {!requested && (
        <form onSubmit={requestOtp}>
          <div className="form-group">
            <label htmlFor="reset-email">Email tài khoản</label>
            <input id="reset-email" className="form-control" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} placeholder="name@example.com" />
          </div>
          <button className="btn btn-primary btn-full" type="submit" disabled={loading}>
            {loading ? 'Đang gửi mã...' : 'Gửi mã OTP'}
          </button>
        </form>
      )}

      {requested && !completed && (
        <form onSubmit={resetPassword}>
          <div className="form-group">
            <label htmlFor="reset-email-display">Email tài khoản</label>
            <input id="reset-email-display" className="form-control" type="email" value={email} readOnly />
          </div>
          <div className="form-group">
            <label htmlFor="reset-otp">Mã OTP</label>
            <input id="reset-otp" className="form-control" inputMode="numeric" autoComplete="one-time-code" maxLength={6} required value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="Nhập mã 6 chữ số" />
          </div>
          <div className="form-group">
            <label htmlFor="new-password">Mật khẩu mới</label>
            <input id="new-password" className="form-control" type="password" autoComplete="new-password" minLength={6} maxLength={100} required value={newPassword} onChange={e => setNewPassword(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="confirm-password">Nhập lại mật khẩu mới</label>
            <input id="confirm-password" className="form-control" type="password" autoComplete="new-password" minLength={6} maxLength={100} required value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} />
          </div>
          <button className="btn btn-primary btn-full" type="submit" disabled={loading}>
            {loading ? 'Đang cập nhật...' : 'Xác nhận mật khẩu mới'}
          </button>
          <button className="btn btn-ghost btn-full" type="button" disabled={loading} onClick={() => void sendOtp()} style={{ marginTop: 10 }}>
            Gửi lại mã OTP
          </button>
        </form>
      )}

      {completed && <Link className="btn btn-primary btn-full" to="/login">Đến trang đăng nhập</Link>}
    </AuthShell>
  )
}

export function CustomerForgotPasswordPage() {
  return <ForgotPasswordFlow staffMode={false} />
}

export function StaffForgotPasswordPage() {
  return <ForgotPasswordFlow staffMode />
}
