import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { authApi } from '@shared/api/auth'
import { AuthShell, authStyles as s } from '@shared/ui/AuthShell'
import { isValidGmail, isValidPersonName, isValidRegionalPhone } from '@shared/lib/contactValidation'

type FieldName = 'fullname' | 'email' | 'password' | 'phone'
type FieldErrors = Partial<Record<FieldName, string>>

// Kiểm tra cả form một lần để báo hết lỗi ngay dưới từng ô, thay vì từng lỗi một.
function validate(v: Record<FieldName, string>): FieldErrors {
  const e: FieldErrors = {}
  if (!v.fullname) e.fullname = 'Vui lòng nhập họ và tên.'
  else if (!isValidPersonName(v.fullname)) e.fullname = 'Họ tên cần từ 2 đến 100 ký tự, chỉ gồm chữ cái, dấu cách, dấu nháy hoặc gạch nối.'
  if (!v.email) e.email = 'Vui lòng nhập Gmail.'
  else if (!isValidGmail(v.email)) e.email = 'Chỉ nhận địa chỉ Gmail. Ví dụ: ten@gmail.com.'
  if (!v.password) e.password = 'Vui lòng nhập mật khẩu.'
  else if (v.password.length < 6) e.password = 'Mật khẩu cần ít nhất 6 ký tự.'
  if (!v.phone) e.phone = 'Vui lòng nhập số điện thoại.'
  else if (!isValidRegionalPhone(v.phone)) e.phone = 'Số điện thoại cần thuộc Việt Nam, Campuchia hoặc Lào.'
  return e
}

// Dòng dưới ô nhập: hiện lỗi nếu có, không thì hiện gợi ý. Ô nhập trỏ tới dòng này qua aria-describedby.
function Note({ id, error, hint }: { id: string; error?: string; hint?: string }) {
  if (error) return <p id={`${id}-note`} className="form-error">{error}</p>
  return hint ? <p id={`${id}-note`} className="form-hint">{hint}</p> : null
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  const field = (name: FieldName, hasNote = true) => ({
    id: name,
    name,
    className: `form-control${fieldErrors[name] ? ' invalid' : ''}`,
    'aria-invalid': fieldErrors[name] ? true : undefined,
    'aria-describedby': hasNote || fieldErrors[name] ? `${name}-note` : undefined,
    onChange: () => {
      setError('')
      if (fieldErrors[name]) setFieldErrors(prev => ({ ...prev, [name]: undefined }))
    },
  })

  const submit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')

    const form = e.currentTarget
    const data = new FormData(form)
    const values = {
      fullname: (data.get('fullname') as string).trim(),
      email: (data.get('email') as string).trim(),
      password: (data.get('password') as string) ?? '',
      phone: (data.get('phone') as string).trim(),
    }
    const address = (data.get('address') as string)?.trim() || ''

    const errors = validate(values)
    setFieldErrors(errors)
    const firstInvalid = (Object.keys(values) as FieldName[]).find(name => errors[name])
    if (firstInvalid) {
      (form.elements.namedItem(firstInvalid) as HTMLInputElement).focus()
      return
    }

    setLoading(true)
    try {
      await authApi.register({
        // Sử dụng email làm username nếu không có trường username riêng
        username: values.email,
        email: values.email,
        password: values.password,
        fullName: values.fullname,
        phone: values.phone,
        address,
      })
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Đăng ký thất bại. Vui lòng thử lại.'
      if ((err as { status?: number }).status === 409) {
        setFieldErrors({ email: message })
        ;(form.elements.namedItem('email') as HTMLInputElement).focus()
      } else {
        setError(message)
      }
      setLoading(false)
      return
    }
    // Tài khoản đã tạo, máy chủ đã gửi OTP về Gmail: nhập đúng mã thì đăng nhập luôn
    setLoading(false)
    await navigate('/verify-email', { state: { email: values.email, from } })
  }

  return (
    <AuthShell title="Tạo tài khoản" subtitle="Tạo tài khoản để đặt chuyến và theo dõi đơn của bạn." back={<Link to="/"><i className="fa-solid fa-arrow-left" /> Về Trang chủ</Link>}>
      <form onSubmit={submit} noValidate>
        {error && (
          <div className={`alert alert-danger ${s.error}`} role="alert">
            <i className="fa-solid fa-circle-exclamation" />
            <div>{error}</div>
          </div>
        )}
        <div className="form-group">
          <label htmlFor="fullname" className="required">Họ và tên</label>
          <input {...field('fullname', false)} type="text" placeholder="Nhập họ và tên" autoComplete="name" maxLength={100} />
          <Note id="fullname" error={fieldErrors.fullname} />
        </div>
        <div className="form-group">
          <label htmlFor="email" className="required">Gmail</label>
          <input {...field('email')} type="email" placeholder="ten@gmail.com" autoComplete="email" />
          <Note id="email" error={fieldErrors.email} hint="Dùng để đăng nhập. Chúng tôi gửi mã xác thực tới Gmail này." />
        </div>
        <div className="form-group">
          <label htmlFor="password" className="required">Mật khẩu</label>
          <div className={s.pwdWrap}>
            <input {...field('password')} type={showPwd ? 'text' : 'password'} autoComplete="new-password" maxLength={100} />
            <button type="button" className={s.pwdToggle} onClick={() => setShowPwd(v => !v)} aria-label={showPwd ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>
              <i className={`fa-solid ${showPwd ? 'fa-eye-slash' : 'fa-eye'}`} />
            </button>
          </div>
          <Note id="password" error={fieldErrors.password} hint="Tối thiểu 6 ký tự." />
        </div>
        <div className="form-group">
          <label htmlFor="phone" className="required">Số điện thoại</label>
          <input {...field('phone')} type="tel" placeholder="0912 345 678" autoComplete="tel" />
          <Note id="phone" error={fieldErrors.phone} hint="Số Việt Nam, Campuchia hoặc Lào." />
        </div>
        <div className="form-group">
          <label htmlFor="address">Địa chỉ / Trang trại <span className="text-muted">(không bắt buộc)</span></label>
          <input className="form-control" id="address" name="address" type="text" placeholder="Địa chỉ trang trại hoặc nơi ở" autoComplete="street-address" />
        </div>

        <button type="submit" className="btn btn-primary btn-full btn-lg" disabled={loading}>
          {loading ? 'Đang tạo tài khoản...' : 'Đăng ký'}
        </button>
      </form>
      <div className={s.switch}>Đã có tài khoản? <Link to="/login" state={{ from }}>Đăng nhập</Link></div>
    </AuthShell>
  )
}
