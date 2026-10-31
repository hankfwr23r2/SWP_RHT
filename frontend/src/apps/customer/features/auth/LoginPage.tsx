import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { isValidEmail } from '@shared/lib/contactValidation'
import { useCustomerSignIn } from './useCustomerSignIn'

export default function LoginPage() {
  const signIn = useCustomerSignIn()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState('')
  const [errorField, setErrorField] = useState<'email' | 'password' | null>(null)
  const [loading, setLoading] = useState(false)
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
    if (!email) return fail('Vui lòng nhập email.', 'email')
    if (!isValidEmail(email)) return fail('Email không đúng định dạng. Ví dụ: ten@example.com.', 'email')
    if (!password) return fail('Vui lòng nhập mật khẩu.', 'password')

    setLoading(true)
    // Tải trước trang chủ khách song song với request đăng nhập, để vào trang là gọi dữ liệu ngay
    if (!from) void import('../portal/PortalPage')
    try {
      const res = await authApi.loginCustomer({ username: email, password })
      // Email chưa xác thực: máy chủ không cấp token mà gửi OTP, sang trang nhập mã
      if (res.emailVerificationRequired) await navigate('/verify-email', { state: { email: res.email, from } })
      else await signIn(res, from)
    } catch (err) {
      fail(err instanceof Error ? err.message : 'Đăng nhập không thành công. Vui lòng thử lại.', 'password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell title="Đăng nhập vào tài khoản" subtitle="Đăng nhập để đặt chuyến và theo dõi đơn của bạn." back={<Link to="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</Link>}>
      <form onSubmit={submit} noValidate>
        {error && <div id="login-error" className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input className={`form-control${errorField === 'email' ? ' invalid' : ''}`} type="email" id="email" name="email" placeholder="ten@example.com" autoComplete="email" aria-invalid={errorField === 'email' || undefined} aria-describedby={errorField === 'email' ? 'login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
        </div>
        <div className="form-group">
          <label htmlFor="password">Mật khẩu</label>
          <div className={s.pwdWrap}>
            <input className={`form-control${errorField === 'password' ? ' invalid' : ''}`} type={showPwd ? 'text' : 'password'} id="password" name="password" autoComplete="current-password" aria-invalid={errorField === 'password' || undefined} aria-describedby={errorField === 'password' ? 'login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
            <button type="button" className={s.pwdToggle} onClick={() => setShowPwd(v => !v)} aria-label={showPwd ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
        </div>
        <p className="text-right small" style={{ marginBottom: 16 }}><Link to="/forgot-password" className="text-orange">Quên mật khẩu?</Link></p>
        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <div className={s.switch}>Chưa có tài khoản? <Link to="/register" state={{ from }}>Đăng ký</Link></div>
      <div className={s.extra}>Bạn là nhân viên? <a href="/backoffice/login">Đăng nhập nội bộ</a></div>
    </AuthShell>
  )
}
