import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { useCustomerSignIn } from './useCustomerSignIn'

export default function LoginPage() {
  const signIn = useCustomerSignIn()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    const data = new FormData(e.currentTarget)
    const email = (data.get('email') as string).trim()
    const password = (data.get('password') as string) ?? ''

    if (!email) { setError('Vui lòng nhập email.'); return }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setError('Email không hợp lệ. Vui lòng kiểm tra lại.'); return }
    if (!password) { setError('Vui lòng nhập mật khẩu.'); return }

    setLoading(true)
    // Tải trước trang chủ khách song song với request đăng nhập, để vào trang là gọi dữ liệu ngay
    if (!from) void import('../portal/PortalPage')
    try {
      const res = await authApi.loginCustomer({ username: email, password })
      // Email chưa xác thực: máy chủ đã gửi OTP, sang trang nhập mã
      if (res.emailVerificationRequired) await navigate('/verify-email', { state: { email: res.email, from } })
      else await signIn(res, from)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng nhập thất bại. Vui lòng kiểm tra lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell title="Đăng nhập vào tài khoản" subtitle="Đăng nhập để tiếp tục sử dụng dịch vụ vận chuyển chuyên nghiệp" back={<Link to="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</Link>}>
      <form onSubmit={submit} noValidate>
        {error && <div className={`alert alert-danger ${s.error}`}><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input className="form-control" type="email" id="email" name="email" placeholder="example@gmail.com" autoComplete="email" onChange={() => setError('')} />
        </div>
        <div className="form-group">
          <label htmlFor="password">Mật khẩu</label>
          <div className={s.pwdWrap}>
            <input className="form-control" type={showPwd ? 'text' : 'password'} id="password" name="password" placeholder="••••••••" autoComplete="current-password" />
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
      <div className={s.switch}>Nếu bạn chưa có tài khoản, bạn có thể <Link to="/register">Đăng ký tại đây!</Link></div>
      <div className={s.extra}>Dành cho nội bộ: <a href="/backoffice/login">Đăng nhập nhân viên</a></div>
    </AuthShell>
  )
}
