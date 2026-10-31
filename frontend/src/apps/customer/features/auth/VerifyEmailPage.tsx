// Nhập OTP xác thực email: sau khi đăng ký, hoặc khi tài khoản chưa xác thực đăng nhập. Đúng mã thì đăng nhập luôn.
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { useCustomerSignIn } from './useCustomerSignIn'

export default function VerifyEmailPage() {
  const state = useLocation().state as { email?: string; from?: string } | null
  const email = state?.email
  const signIn = useCustomerSignIn()
  const [otp, setOtp] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(false)

  if (!email) return <Navigate to="/login" replace />

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    if (otp.length !== 6) { setError('Mã OTP gồm 6 chữ số.'); return }
    setLoading(true)
    try {
      await signIn(await authApi.confirmEmail(email, otp), state?.from)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Xác thực thất bại. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const resend = async () => {
    setError('')
    setLoading(true)
    try {
      setMessage(await authApi.resendEmailOtp(email))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không gửi lại được mã. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell title="Xác thực email" subtitle={`Nhập mã 6 chữ số đã gửi tới ${email}. Mã có hiệu lực 10 phút.`} back={<Link to="/login"><i className="fa-solid fa-arrow-left" /> Quay lại đăng nhập</Link>}>
      <form onSubmit={submit} noValidate>
        {error && <div className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        {message && !error && <div className="alert alert-success" role="status">{message}</div>}
        <div className="form-group">
          <label htmlFor="email-otp">Mã OTP</label>
          <input id="email-otp" className="form-control" inputMode="numeric" autoComplete="one-time-code" maxLength={6} autoFocus value={otp} onChange={e => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); setError('') }} placeholder="Nhập mã 6 chữ số" />
        </div>
        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? 'Đang xác thực...' : 'Xác thực'}
        </button>
        <button type="button" className="btn btn-ghost btn-full" disabled={loading} onClick={() => void resend()} style={{ marginTop: 10 }}>
          Gửi lại mã OTP
        </button>
      </form>
    </AuthShell>
  )
}
