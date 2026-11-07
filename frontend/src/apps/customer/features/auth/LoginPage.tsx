import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { TurnstileWidget } from '@shared/ui/TurnstileWidget'
import { isValidEmail } from '@shared/lib/contactValidation'
import { useCustomerSignIn } from './useCustomerSignIn'

export default function LoginPage() {
  const { t } = useTranslation()
  const signIn = useCustomerSignIn()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState('')
  const [errorField, setErrorField] = useState<'email' | 'password' | null>(null)
  const [loading, setLoading] = useState(false)
  const [captchaToken, setCaptchaToken] = useState<string | null>(null)
  const [captchaGeneration, setCaptchaGeneration] = useState(0)
  const [showPwd, setShowPwd] = useState(false)

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    const form = e.currentTarget
    const data = new FormData(form)
    const email = (data.get('email') as string).trim()
    const password = (data.get('password') as string) ?? ''

    // Báo lỗi và đưa con trỏ về đúng ô cần sửa
    const fail = (message: string, field: 'email' | 'password') => {
      setError(message)
      setErrorField(field)
      ;(form.elements.namedItem(field) as HTMLInputElement).focus()
    }
    if (!email) return fail(t('auth.emailRequired'), 'email')
    if (!isValidEmail(email)) return fail(t('auth.emailInvalid'), 'email')
    if (!password) return fail(t('auth.passwordRequired'), 'password')
    if (!captchaToken) {
      setError(t('captcha.required'))
      return
    }

    setLoading(true)
    // Tải trước trang chủ khách song song với request đăng nhập, để vào trang là gọi dữ liệu ngay
    if (!from) void import('../portal/PortalPage')
    try {
      const res = await authApi.loginCustomer({ username: email, password }, captchaToken)
      // Email chưa xác thực: máy chủ không cấp token mà gửi OTP, sang trang nhập mã
      if (res.emailVerificationRequired) await navigate('/verify-email', { state: { email: res.email, from } })
      else await signIn(res, from)
    } catch (err) {
      fail(err instanceof Error ? err.message : t('auth.loginFailed'), 'password')
    } finally {
      setLoading(false)
      setCaptchaToken(null)
      setCaptchaGeneration(generation => generation + 1)
    }
  }

  return (
    <AuthShell title={t('auth.loginToAccount')} subtitle={t('auth.loginSubtitle')} back={<Link to="/"><i className="fa-solid fa-arrow-left" /> {t('auth.backToHome')}</Link>}>
      <form onSubmit={submit} noValidate>
        {error && <div id="login-error" className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        <div className="form-group">
          <label htmlFor="email">{t('auth.email')}</label>
          <input className={`form-control${errorField === 'email' ? ' invalid' : ''}`} type="email" id="email" name="email" placeholder="ten@example.com" autoComplete="email" aria-invalid={errorField === 'email' || undefined} aria-describedby={errorField === 'email' ? 'login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
        </div>
        <div className="form-group">
          <label htmlFor="password">{t('auth.password')}</label>
          <div className={s.pwdWrap}>
            <input className={`form-control${errorField === 'password' ? ' invalid' : ''}`} type={showPwd ? 'text' : 'password'} id="password" name="password" autoComplete="current-password" aria-invalid={errorField === 'password' || undefined} aria-describedby={errorField === 'password' ? 'login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
            <button type="button" className={s.pwdToggle} onClick={() => setShowPwd(v => !v)} aria-label={showPwd ? t('auth.hidePassword') : t('auth.showPassword')}>
              <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
        </div>
        <TurnstileWidget key={captchaGeneration} action="customer_login" onTokenChange={setCaptchaToken} />
        <p className="text-right small" style={{ marginBottom: 16 }}><Link to="/forgot-password" className="text-orange">{t('auth.forgotPassword')}</Link></p>
        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? t('auth.loggingIn') : t('auth.signIn')}
        </button>
      </form>
      <div className={s.switch}>{t('auth.noAccount')} <Link to="/register" state={{ from }}>{t('auth.signUp')}</Link></div>
      <div className={s.extra}>{t('auth.staffPrompt')} <a href="/backoffice/login">{t('auth.staffLogin')}</a></div>
    </AuthShell>
  )
}
