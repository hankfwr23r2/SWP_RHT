import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
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
  const [loading, setLoading] = useState(false)

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    const data = new FormData(e.currentTarget)
    const email = (data.get('email') as string).trim()
    const password = (data.get('password') as string) ?? ''

    try {
      const res = await authApi.loginStaff({ username: email, password })
      const rawRole = (res.role || 'manager').toLowerCase()
      const role = rawRole as StaffRole

      if (managerOnly && role !== 'manager' && role !== 'admin') {
        setError('Tài khoản của bạn không có quyền truy cập vào cổng Quản lý.')
        return
      }

      tokenStorage.set(res.accessToken)
      login({
        role,
        name: res.fullName || res.staffCode || res.username,
        username: res.email || res.username,
      })
      navigate(HOME_OF[role] ?? '/manager')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng nhập thất bại. Vui lòng kiểm tra lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={submit}>
      {error && <div className={`alert alert-danger ${s.error}`}><i className="fa-solid fa-circle-exclamation" /><div>{error}</div></div>}
      <div className="form-group">
        <label htmlFor="email">{managerOnly ? 'Tên đăng nhập / Email' : 'Email'}</label>
        <input className="form-control" id="email" name="email" placeholder={managerOnly ? 'manager@equine.vn' : 'VD: manager, coordinator...'} required onChange={() => setError('')} />
      </div>
      <div className="form-group">
        <label htmlFor="password">Mật khẩu</label>
        <input className="form-control" id="password" name="password" type="password" placeholder="••••••••" required />
      </div>
      <p className="text-right small" style={{ marginBottom: 16 }}><a href="#" className="text-orange">Quên mật khẩu?</a></p>
      <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
        {loading ? 'Đang xác thực...' : managerOnly ? 'Đăng nhập Quản lý' : 'Đăng nhập'}
      </button>
    </form>
  )
}

export function StaffLoginPage() {
  return (
    <AuthShell title="Đăng nhập Nội bộ" subtitle="Vui lòng đăng nhập bằng email được cấp" heading="Hệ thống Nội bộ" tagline="Cổng đăng nhập dành cho nhân viên. Quản lý, tài xế, hộ tống và vận hành." homeHref="/" homeExternal back={<a href="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</a>}>
      <LoginForm managerOnly={false} />
    </AuthShell>
  )
}

export function ManagerLoginPage() {
  return (
    <AuthShell title="Cổng Quản lý" subtitle="Vui lòng đăng nhập với tài khoản cấp quản lý" heading="Hệ thống Quản lý Vận hành" tagline="Nền tảng kiểm soát và điều phối toàn diện lộ trình vận chuyển ngựa đua an toàn, tiêu chuẩn." homeHref="/" homeExternal back={<a href="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</a>}>
      <LoginForm managerOnly />
    </AuthShell>
  )
}
