import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { authApi } from '@shared/api/auth'
import { tokenStorage } from '@shared/api/client'
import type { StaffRole } from '@shared/types/role'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { HOME_OF } from '../../layouts/staffMenus'

function LoginForm({ managerOnly }: { managerOnly: boolean }) {
  const { login } = useAuth()
  const navigate = useNavigate()
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
    if (!email) return fail('Vui lòng nhập email hoặc tên đăng nhập.', 'email')
    if (!password) return fail('Vui lòng nhập mật khẩu.', 'password')

    setLoading(true)
    try {
      const res = await authApi.loginStaff({ username: email, password })
      const rawRole = (res.role || 'manager').toLowerCase()
      // Backend stores inspector staff as INSPECTOR; the backoffice route
      // configuration and menus use the product-facing role name specialist.
      const role = (rawRole === 'inspector' ? 'specialist' : rawRole) as StaffRole

      if (managerOnly && role !== 'manager' && role !== 'admin') {
        setError('Tài khoản của bạn không có quyền vào cổng quản lý.')
        return
      }

      tokenStorage.set(res.accessToken)
      login({
        role,
        name: res.fullName || res.username,
        username: res.email || res.username,
        staffCode: res.staffCode,
      })
      navigate(HOME_OF[role] ?? '/manager')
    } catch (err) {
      fail(err instanceof Error ? err.message : 'Đăng nhập không thành công. Vui lòng thử lại.', 'password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      {error && <div id="staff-login-error" className={`alert alert-danger ${s.error}`} role="alert"><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
      <div className="form-group">
        <label htmlFor="email">Email hoặc tên đăng nhập</label>
        <input className={`form-control${errorField === 'email' ? ' invalid' : ''}`} id="email" name="email" autoComplete="username" aria-invalid={errorField === 'email' || undefined} aria-describedby={errorField === 'email' ? 'staff-login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
      </div>
      <div className="form-group">
        <label htmlFor="password">Mật khẩu</label>
        <div className={s.pwdWrap}>
          <input className={`form-control${errorField === 'password' ? ' invalid' : ''}`} id="password" name="password" type={showPwd ? 'text' : 'password'} autoComplete="current-password" aria-invalid={errorField === 'password' || undefined} aria-describedby={errorField === 'password' ? 'staff-login-error' : undefined} onChange={() => { setError(''); setErrorField(null) }} />
          <button type="button" className={s.pwdToggle} onClick={() => setShowPwd(v => !v)} aria-label={showPwd ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
            <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
          </button>
        </div>
      </div>
      <p className="text-right small" style={{ marginBottom: 16 }}><Link to="/forgot-password" className="text-orange">Quên mật khẩu?</Link></p>
      <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
        {loading ? 'Đang đăng nhập...' : managerOnly ? 'Đăng nhập quản lý' : 'Đăng nhập'}
      </button>
    </form>
  )
}

export function StaffLoginPage() {
  return (
    <AuthShell title="Đăng nhập nội bộ" subtitle="Đăng nhập bằng tài khoản được cấp." heading="Hệ thống nội bộ" tagline="Dành cho quản lý, chuyên viên, điều phối viên, tài xế và hộ tống." points={[]} homeHref="/" homeExternal back={<a href="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</a>}>
      <LoginForm managerOnly={false} />
      <div className={s.extra}>Bạn là khách hàng? <a href="/login">Đăng nhập khách hàng</a></div>
    </AuthShell>
  )
}

export function ManagerLoginPage() {
  return (
    <AuthShell title="Cổng quản lý" subtitle="Đăng nhập bằng tài khoản quản lý." heading="Hệ thống quản lý vận hành" tagline="Tiếp nhận đơn, duyệt báo giá và xử lý sự cố." points={[]} homeHref="/" homeExternal back={<a href="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</a>}>
      <LoginForm managerOnly />
      <div className={s.extra}>Bạn là khách hàng? <a href="/login">Đăng nhập khách hàng</a></div>
    </AuthShell>
  )
}
