// Nhập OTP xác thực email: sau khi đăng ký, hoặc khi tài khoản chưa xác thực đăng nhập. Đúng mã thì đăng nhập luôn.
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { useCustomerSignIn } from './useCustomerSignIn'

export default function VerifyEmailPage() {
  const { t } = useTranslation()
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
    if (otp.length !== 6) {
      setError('Mã OTP gồm 6 chữ số.')
      document.getElementById('email-otp')?.focus()
      return
    }
    setLoading(true)
    try {
      await signIn(await authApi.confirmEmail(email, otp), state?.from)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Xác thực không thành công. Vui lòng thử lại.')
      document.getElementById('email-otp')?.focus()
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
    <AuthShell title={t('authPages.verifyEmailTitle')} subtitle={t('authPages.verifyEmailSubtitle', { email })} back={<Link to="/login"><i className="fa-solid fa-arrow-left" /> {t('authPages.backToLogin')}</Link>}>
      <form onSubmit={submit} noValidate>
        {error && <div id="email-otp-error" className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        {message && !error && <div className={`alert alert-success ${s.error}`} role="status"><i className="fa-solid fa-circle-check" /><div>{message}</div></div>}
        <div className="form-group">
          <label htmlFor="email-otp">{t('authPages.otpLabel')}</label>
          <input id="email-otp" className={`form-control${error ? ' invalid' : ''}`} aria-invalid={!!error || undefined} aria-describedby={error ? 'email-otp-error' : undefined} inputMode="numeric" autoComplete="one-time-code" maxLength={6} autoFocus value={otp} onChange={e => { setOtp(e.target.value.replace(/\D/g, '').slice(0, 6)); setError('') }} placeholder={t('authPages.otpPlaceholder')} />
        </div>
        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? t('authPages.verifying') : t('authPages.verifyBtn')}
        </button>
        <button type="button" className="btn btn-ghost btn-full btn-lg" disabled={loading} onClick={() => void resend()} style={{ marginTop: 10 }}>
          {t('authPages.resendOtp')}
        </button>
      </form>
    </AuthShell>
  )
}
