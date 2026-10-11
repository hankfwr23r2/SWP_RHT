import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { authApi } from '@shared/api/auth'
import { tokenStorage } from '@shared/api/client'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    const data = new FormData(e.currentTarget)
    const email = (data.get('email') as string).trim()
    const password = (data.get('password') as string) ?? ''

    try {
      const res = await authApi.loginCustomer({ username: email, password })
      tokenStorage.set(res.accessToken)
      login({
        role: 'customer',
        name: res.fullName || res.username,
        username: res.email || res.username,
      })
      navigate(from ?? '/portal')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng nhập thất bại. Vui lòng kiểm tra lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell title="Đăng nhập vào tài khoản" subtitle="Đăng nhập để tiếp tục sử dụng dịch vụ vận chuyển chuyên nghiệp" back={<Link to="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</Link>}>
      <form onSubmit={submit}>
        {error && <div className={`alert alert-danger ${s.error}`}><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input className="form-control" type="email" id="email" name="email" placeholder="example@gmail.com" required onChange={() => setError('')} />
        </div>
        <div className="form-group">
          <label htmlFor="password">Mật khẩu</label>
          <input className="form-control" type="password" id="password" name="password" placeholder="••••••••" required />
        </div>
        <p className="text-right small" style={{ marginBottom: 16 }}><a href="#" className="text-orange">Quên mật khẩu?</a></p>
        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
      </form>
      <div className={s.switch}>Nếu bạn chưa có tài khoản, bạn có thể <Link to="/register">Đăng ký tại đây!</Link></div>
      <div className={s.extra}>Dành cho nội bộ: <a href="/backoffice/login">Đăng nhập nhân viên</a></div>
    </AuthShell>
  )
}
