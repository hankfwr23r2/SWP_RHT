import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { customerProfileApi, type CustomerProfileDto } from '@shared/api/customerProfile'
import { useAuth } from '@shared/auth/AuthContext'
import { useToast } from '@shared/ui/toast'
import s from './Profile.module.css'

export default function ProfilePage() {
  const { session, login } = useAuth()
  const toast = useToast()

  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState<CustomerProfileDto | null>(null)
  const [tab, setTab] = useState<'info' | 'security'>('info')

  // Form Thông tin cá nhân
  const [fullName, setFullName] = useState('')
  const [phone, setPhone] = useState('')
  const [address, setAddress] = useState('')
  const [savingInfo, setSavingInfo] = useState(false)

  // Form Đổi mật khẩu
  const [curPassword, setCurPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [savingPass, setSavingPass] = useState(false)

  useEffect(() => {
    loadProfile()
  }, [])

  const loadProfile = async () => {
    try {
      setLoading(true)
      const data = await customerProfileApi.getProfile()
      setProfile(data)
      setFullName(data.fullName || '')
      setPhone(data.phone || '')
      setAddress(data.address || '')
    } catch {
      // Fallback nếu mock data trong test
      if (session) {
        setProfile({
          userId: 1,
          username: session.username,
          email: session.username.includes('@') ? session.username : `${session.username}@example.com`,
          fullName: session.name || 'Khách hàng',
          phone: '',
          address: '',
          totalHorses: 0,
          totalBookings: 0,
        })
        setFullName(session.name || 'Khách hàng')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleUpdateInfo = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!fullName.trim()) {
      toast('Vui lòng nhập Họ và tên', 'error')
      return
    }

    try {
      setSavingInfo(true)
      const updated = await customerProfileApi.updateProfile({
        fullName: fullName.trim(),
        phone: phone.trim() || undefined,
        address: address.trim() || undefined,
      })
      setProfile(updated)
      toast('Cập nhật thông tin cá nhân thành công!', 'success')

      // Cập nhật session tên hiển thị
      if (session) {
        login({
          ...session,
          name: updated.fullName,
        })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Không thể cập nhật hồ sơ'
      toast(msg, 'error')
    } finally {
      setSavingInfo(false)
    }
  }

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!curPassword) {
      toast('Vui lòng nhập mật khẩu hiện tại', 'error')
      return
    }
    if (!newPassword || newPassword.length < 6) {
      toast('Mật khẩu mới phải có ít nhất 6 ký tự', 'error')
      return
    }
    if (newPassword !== confirmPassword) {
      toast('Xác nhận mật khẩu mới không trùng khớp', 'error')
      return
    }

    try {
      setSavingPass(true)
      await customerProfileApi.changePassword({
        currentPassword: curPassword,
        newPassword,
        confirmPassword,
      })
      toast('Đổi mật khẩu thành công!', 'success')
      setCurPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Đổi mật khẩu thất bại, vui lòng kiểm tra lại mật khẩu hiện tại'
      toast(msg, 'error')
    } finally {
      setSavingPass(false)
    }
  }

  const initialsOf = (name = '') =>
    name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase() || 'KH'

  if (loading && !profile) {
    return (
      <div className={s.page}>
        <div className={s.loadingBox}>
          <i className="fa-solid fa-spinner fa-spin fa-2x" />
          <p>Đang tải thông tin hồ sơ...</p>
        </div>
      </div>
    )
  }

  return (
    <div className={s.page}>
      <div className={s.topBar}>
        <Link to="/portal" className={s.backBtn}>
          <i className="fa-solid fa-arrow-left" /> Quay lại Trang chủ
        </Link>
      </div>

      {/* Hero Profile Card */}
      <div className={s.heroCard}>
        <div className={s.profileInfo}>
          <div className={s.avatarBig}>
            {initialsOf(profile?.fullName || session?.name)}
          </div>
          <div className={s.nameBlock}>
            <h1>{profile?.fullName || session?.name || 'Khách hàng'}</h1>
            <div className={s.metaRow}>
              <span><i className="fa-regular fa-user" /> @{profile?.username || session?.username}</span>
              <span><i className="fa-regular fa-envelope" /> {profile?.email}</span>
              <span className={s.roleBadge}>Khách hàng</span>
            </div>
          </div>
        </div>

        <div className={s.statsRow}>
          <div className={s.statBox}>
            <div className={s.statVal}>{profile?.totalHorses ?? 0}</div>
            <div className={s.statLabel}>Hồ sơ ngựa</div>
          </div>
          <div className={s.statBox}>
            <div className={s.statVal}>{profile?.totalBookings ?? 0}</div>
            <div className={s.statLabel}>Chuyến vận chuyển</div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className={s.tabs}>
        <button
          type="button"
          className={`${s.tabBtn} ${tab === 'info' ? s.activeTab : ''}`}
          onClick={() => setTab('info')}
        >
          <i className="fa-regular fa-id-card" /> Thông tin cá nhân
        </button>
        <button
          type="button"
          className={`${s.tabBtn} ${tab === 'security' ? s.activeTab : ''}`}
          onClick={() => setTab('security')}
        >
          <i className="fa-solid fa-shield-halved" /> Bảo mật & Mật khẩu
        </button>
      </div>

      {/* Tab 1: Thông tin cá nhân */}
      {tab === 'info' && (
        <div className={s.card}>
          <div className={s.cardHeader}>
            <h2>Hồ sơ cá nhân</h2>
            <p>Quản lý tên hiển thị, số điện thoại liên lạc và địa chỉ giao dịch của bạn.</p>
          </div>

          <form onSubmit={handleUpdateInfo}>
            <div className={s.formGrid}>
              <div className={s.field}>
                <label>Tên đăng nhập (Username)</label>
                <input type="text" value={profile?.username || ''} disabled />
                <span className={s.fieldHint}>Tên đăng nhập cố định của tài khoản</span>
              </div>

              <div className={s.field}>
                <label>Email đăng ký</label>
                <input type="email" value={profile?.email || ''} disabled />
                <span className={s.fieldHint}>Email dùng để nhận thông báo và hóa đơn</span>
              </div>

              <div className={s.field}>
                <label>Họ và tên hiển thị <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="text"
                  value={fullName}
                  onChange={e => setFullName(e.target.value)}
                  placeholder="Nhập họ và tên đầy đủ"
                  required
                />
              </div>

              <div className={s.field}>
                <label>Số điện thoại liên hệ</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  placeholder="Ví dụ: 0912 345 678"
                />
              </div>

              <div className={`${s.field} ${s.formFull}`}>
                <label>Địa chỉ liên hệ / Trang trại</label>
                <textarea
                  rows={3}
                  value={address}
                  onChange={e => setAddress(e.target.value)}
                  placeholder="Nhập địa chỉ trang trại hoặc nơi nhận hóa đơn..."
                />
              </div>
            </div>

            <div className={s.actionRow}>
              <button type="submit" className={s.saveBtn} disabled={savingInfo}>
                {savingInfo ? <i className="fa-solid fa-spinner fa-spin" /> : <i className="fa-solid fa-floppy-disk" />}
                Lưu thay đổi
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Tab 2: Bảo mật & Đổi mật khẩu */}
      {tab === 'security' && (
        <div className={s.card}>
          <div className={s.cardHeader}>
            <h2>Bảo mật & Đổi mật khẩu</h2>
            <p>Để đảm bảo an toàn cho tài khoản, vui lòng sử dụng mật khẩu mạnh tối thiểu 6 ký tự.</p>
          </div>

          <form onSubmit={handleChangePassword}>
            <div className={s.formGrid}>
              <div className={`${s.field} ${s.formFull}`}>
                <label>Mật khẩu hiện tại <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={curPassword}
                  onChange={e => setCurPassword(e.target.value)}
                  placeholder="Nhập mật khẩu hiện tại của bạn"
                  required
                />
              </div>

              <div className={s.field}>
                <label>Mật khẩu mới <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)"
                  required
                />
              </div>

              <div className={s.field}>
                <label>Xác nhận mật khẩu mới <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder="Nhập lại mật khẩu mới"
                  required
                />
              </div>
            </div>

            <div className={s.actionRow}>
              <button type="submit" className={s.saveBtn} disabled={savingPass}>
                {savingPass ? <i className="fa-solid fa-spinner fa-spin" /> : <i className="fa-solid fa-key" />}
                Cập nhật mật khẩu
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}
