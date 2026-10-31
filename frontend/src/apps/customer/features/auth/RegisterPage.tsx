import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { isValidGmail, isValidPersonName, isValidRegionalPhone } from '@shared/lib/contactValidation'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    const form = new FormData(e.currentTarget)
    const email = (form.get('email') as string).trim()
    const password = (form.get('password') as string) ?? ''
    const fullName = (form.get('fullname') as string).trim()
    const phone = (form.get('phone') as string).trim()
    const address = (form.get('address') as string)?.trim() || ''

    if (!isValidPersonName(fullName)) {
      setError('Họ tên cần có từ 2 đến 100 ký tự, chỉ gồm chữ cái và dấu cách, dấu nháy hoặc gạch nối.')
      setLoading(false)
      return
    }
    if (!isValidGmail(email)) {
      setError('Chỉ nhận địa chỉ Gmail. Ví dụ: ten@gmail.com.')
      setLoading(false)
      return
    }
    if (!isValidRegionalPhone(phone)) {
      setError('Số điện thoại cần thuộc Việt Nam, Campuchia hoặc Lào.')
      setLoading(false)
      return
    }

    // Sử dụng email làm username nếu không có trường username riêng
    const username = email

    try {
      await authApi.register({
        username,
        email,
        password,
        fullName,
        phone,
        address,
      })
      // Máy chủ đã gửi OTP về email, nhập đúng mã thì đăng nhập luôn
      navigate('/verify-email', { state: { email } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Đăng ký thất bại. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthShell title="Tạo tài khoản" subtitle="Đăng ký để trải nghiệm dịch vụ vận chuyển chuyên nghiệp">
      <form onSubmit={submit}>
        {error && (
          <div className={`alert alert-danger ${s.error}`}>
            <i className="fa-solid fa-circle-exclamation" />
            <div>{error}</div>
          </div>
        )}
        <div className="form-group">
          <label htmlFor="fullname">Họ và Tên</label>
          <input className="form-control" id="fullname" name="fullname" type="text" placeholder="Nhập họ và tên" autoComplete="name" required minLength={2} maxLength={100} onChange={() => setError('')} />
        </div>
        <div className="form-group">
          <label htmlFor="email">Gmail (dùng làm tên đăng nhập)</label>
          <input className="form-control" id="email" name="email" type="email" placeholder="example@gmail.com" autoComplete="email" required onChange={() => setError('')} />
        </div>
        <div className="form-group">
          <label htmlFor="password">Mật khẩu</label>
          <input className="form-control" id="password" name="password" type="password" placeholder="Tối thiểu 6 ký tự" required minLength={6} />
        </div>
        <div className="form-group">
          <label htmlFor="phone">Số điện thoại</label>
          <input className="form-control" id="phone" name="phone" type="tel" placeholder="+84, +855 hoặc +856" autoComplete="tel" required onChange={() => setError('')} />
        </div>
        <div className="form-group">
          <label htmlFor="address">Địa chỉ / Trang trại</label>
          <input className="form-control" id="address" name="address" type="text" placeholder="Địa chỉ trang trại hoặc nơi ở" />
        </div>

        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? 'Đang xử lý...' : 'Đăng Ký Ngay'}
        </button>
      </form>
      <div className={s.switch}>Bạn đã có tài khoản? <Link to="/login">Đăng nhập</Link></div>
    </AuthShell>
  )
}

