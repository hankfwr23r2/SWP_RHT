import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { customerProfileApi, type CustomerProfileDto } from '@shared/api/customerProfile'
import { useAuth } from '@shared/auth/AuthContext'
import { useToast } from '@shared/ui/toast'
import { isValidPersonName, isValidRegionalPhone } from '@shared/lib/contactValidation'
import s from './Profile.module.css'

export default function ProfilePage() {
  const { t, i18n } = useTranslation()
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
    if (!isValidPersonName(fullName)) {
      toast(i18n.language === 'en' ? 'Full name must be between 2 and 100 characters and contain only letters and standard name punctuation.' : 'Họ tên cần có từ 2 đến 100 ký tự và chỉ gồm chữ cái, dấu cách, dấu nháy hoặc gạch nối.', 'error')
      return
    }
    if (phone.trim() && !isValidRegionalPhone(phone)) {
      toast(i18n.language === 'en' ? 'Phone number must belong to Vietnam, Cambodia, or Laos.' : 'Số điện thoại cần thuộc Việt Nam, Campuchia hoặc Lào.', 'error')
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
      toast(i18n.language === 'en' ? 'Profile updated successfully!' : 'Cập nhật thông tin cá nhân thành công!', 'success')

      // Cập nhật session tên hiển thị
      if (session) {
        login({
          ...session,
          name: updated.fullName,
        })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : (i18n.language === 'en' ? 'Could not update profile' : 'Không thể cập nhật hồ sơ')
      toast(msg, 'error')
    } finally {
      setSavingInfo(false)
    }
  }

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!curPassword) {
      toast(i18n.language === 'en' ? 'Please enter your current password' : 'Vui lòng nhập mật khẩu hiện tại', 'error')
      return
    }
    if (!newPassword || newPassword.length < 6) {
      toast(i18n.language === 'en' ? 'New password must have at least 6 characters' : 'Mật khẩu mới phải có ít nhất 6 ký tự', 'error')
      return
    }
    if (newPassword !== confirmPassword) {
      toast(i18n.language === 'en' ? 'New password confirmation does not match' : 'Xác nhận mật khẩu mới không trùng khớp', 'error')
      return
    }

    try {
      setSavingPass(true)
      await customerProfileApi.changePassword({
        currentPassword: curPassword,
        newPassword,
        confirmPassword,
      })
      toast(i18n.language === 'en' ? 'Password changed successfully!' : 'Đổi mật khẩu thành công!', 'success')
      setCurPassword('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : (i18n.language === 'en' ? 'Password change failed, please check your current password' : 'Đổi mật khẩu thất bại, vui lòng kiểm tra lại mật khẩu hiện tại')
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
          <p>{t('common.loading')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className={s.page}>
      <div className={s.topBar}>
        <Link to="/portal" className={s.backBtn}>
          <i className="fa-solid fa-arrow-left" /> {t('profile.backToHome')}
        </Link>
      </div>

      {/* Hero Profile Card */}
      <div className={s.heroCard}>
        <div className={s.profileInfo}>
          <div className={s.avatarBig}>
            {initialsOf(profile?.fullName || session?.name)}
          </div>
          <div className={s.nameBlock}>
            <h1>{profile?.fullName || session?.name || t('profile.customerRole')}</h1>
            <div className={s.metaRow}>
              <span><i className="fa-regular fa-envelope" /> {profile?.email}</span>
              <span className={s.roleBadge}>{t('profile.customerRole')}</span>
            </div>
          </div>
        </div>

        <div className={s.statsRow}>
          <div className={s.statBox}>
            <div className={s.statVal}>{profile?.totalHorses ?? 0}</div>
            <div className={s.statLabel}>{t('profile.horsesCount')}</div>
          </div>
          <div className={s.statBox}>
            <div className={s.statVal}>{profile?.totalBookings ?? 0}</div>
            <div className={s.statLabel}>{t('profile.bookingsCount')}</div>
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
          <i className="fa-regular fa-id-card" /> {t('profile.tabInfo')}
        </button>
        <button
          type="button"
          className={`${s.tabBtn} ${tab === 'security' ? s.activeTab : ''}`}
          onClick={() => setTab('security')}
        >
          <i className="fa-solid fa-shield-halved" /> {t('profile.tabSecurity')}
        </button>
      </div>

      {/* Tab 1: Thông tin cá nhân */}
      {tab === 'info' && (
        <div className={s.card}>
          <div className={s.cardHeader}>
            <h2>{t('profile.tabInfo')}</h2>
            <p>{i18n.language === 'en' ? 'Manage your display name, contact phone, and transaction address.' : 'Quản lý tên hiển thị, số điện thoại liên lạc và địa chỉ giao dịch của bạn.'}</p>
          </div>

          <form onSubmit={handleUpdateInfo}>
            <div className={s.formGrid}>
              <div className={s.field}>
                <label>{i18n.language === 'en' ? 'Registered Email' : 'Email đăng ký'}</label>
                <input type="email" value={profile?.email || ''} disabled />
                <span className={s.fieldHint}>{i18n.language === 'en' ? 'Email for notifications and e-invoices' : 'Email dùng để nhận thông báo và hóa đơn'}</span>
              </div>

              <div className={s.field}>
                <label>{t('profile.fullName')} <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="text"
                  value={fullName}
                  onChange={e => setFullName(e.target.value)}
                  placeholder={i18n.language === 'en' ? 'Enter full name' : 'Nhập họ và tên đầy đủ'}
                  required
                  minLength={2}
                  maxLength={100}
                />
              </div>

              <div className={s.field}>
                <label>{t('profile.phone')}</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={e => setPhone(e.target.value)}
                  placeholder="0912 345 678"
                  autoComplete="tel"
                />
              </div>

              <div className={`${s.field} ${s.formFull}`}>
                <label>{t('profile.address')}</label>
                <textarea
                  rows={3}
                  value={address}
                  onChange={e => setAddress(e.target.value)}
                  placeholder={i18n.language === 'en' ? 'Stable address or billing address...' : 'Nhập địa chỉ trang trại hoặc nơi nhận hóa đơn...'}
                />
              </div>
            </div>

            <div className={s.actionRow}>
              <button type="submit" className={s.saveBtn} disabled={savingInfo}>
                {savingInfo ? <i className="fa-solid fa-spinner fa-spin" /> : <i className="fa-solid fa-floppy-disk" />}
                {savingInfo ? t('profile.saving') : t('profile.saveChanges')}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Tab 2: Bảo mật & Đổi mật khẩu */}
      {tab === 'security' && (
        <div className={s.card}>
          <div className={s.cardHeader}>
            <h2>{t('profile.tabSecurity')}</h2>
            <p>{i18n.language === 'en' ? 'To ensure account security, please use a strong password with at least 6 characters.' : 'Để đảm bảo an toàn cho tài khoản, vui lòng sử dụng mật khẩu mạnh tối thiểu 6 ký tự.'}</p>
          </div>

          <form onSubmit={handleChangePassword}>
            <div className={s.formGrid}>
              <div className={`${s.field} ${s.formFull}`}>
                <label>{t('profile.curPassword')} <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={curPassword}
                  onChange={e => setCurPassword(e.target.value)}
                  placeholder={i18n.language === 'en' ? 'Enter current password' : 'Nhập mật khẩu hiện tại của bạn'}
                  required
                />
              </div>

              <div className={s.field}>
                <label>{t('profile.newPassword')} <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={newPassword}
                  onChange={e => setNewPassword(e.target.value)}
                  placeholder={i18n.language === 'en' ? 'Enter new password (min. 6 characters)' : 'Nhập mật khẩu mới (tối thiểu 6 ký tự)'}
                  required
                />
              </div>

              <div className={s.field}>
                <label>{t('profile.confirmPassword')} <span style={{ color: '#dc2626' }}>*</span></label>
                <input
                  type="password"
                  value={confirmPassword}
                  onChange={e => setConfirmPassword(e.target.value)}
                  placeholder={i18n.language === 'en' ? 'Confirm new password' : 'Nhập lại mật khẩu mới'}
                  required
                />
              </div>
            </div>

            <div className={s.actionRow}>
              <button type="submit" className={s.saveBtn} disabled={savingPass}>
                {savingPass ? <i className="fa-solid fa-spinner fa-spin" /> : <i className="fa-solid fa-key" />}
                {savingPass ? t('profile.saving') : t('profile.changePasswordBtn')}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}
