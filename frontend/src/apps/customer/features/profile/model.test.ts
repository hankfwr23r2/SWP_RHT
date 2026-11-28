import { describe, expect, it } from 'vitest'
import { passwordErrors, passwordServerError, profileErrors, profileForm, profileRequest, profileServerError, withIdentityType } from './model'

const profile = { userId: 1, email: 'a@gmail.com', accountStatus: 'ACTIVE' as const, customerType: 'PERSON' as const, fullName: 'An', countryCode: 'VN' }

describe('hồ sơ khách', () => {
  it('chỉ gửi các ô được sửa, ô trống không gửi', () => {
    const form = { ...profileForm(profile), fullName: ' An Nguyễn ', phone: ' 0901234567 ' }
    expect(JSON.parse(JSON.stringify(profileRequest(form)))).toEqual({ customerType: 'PERSON', fullName: 'An Nguyễn', phone: '0901234567', countryCode: 'VN' })
  })
  it('giấy tờ: chọn loại thì phải đủ số và nước cấp, bỏ loại thì xóa cả ba', () => {
    const picked = withIdentityType(profileForm(profile), 'CCCD')
    expect(picked.identityDocumentCountryCode).toBe('VN')
    expect(profileErrors(picked)).toEqual({ identityDocumentNumber: 'identityIncomplete' })
    const cleared = withIdentityType({ ...picked, identityDocumentNumber: '079' }, '')
    expect(cleared).toMatchObject({ identityDocumentType: '', identityDocumentNumber: '', identityDocumentCountryCode: '' })
    expect(JSON.parse(JSON.stringify(profileRequest(cleared)))).not.toHaveProperty('identityDocumentNumber')
  })
  it('thiếu tên hoặc quốc gia thì báo ngay dưới ô', () => {
    expect(profileErrors({ ...profileForm(profile), fullName: ' ', countryCode: '' })).toEqual({ fullName: 'required', countryCode: 'required' })
  })
  it('lỗi backend không kèm ô được gắn vào ô cần sửa', () => {
    expect(profileServerError('Giấy tờ này đã được đăng ký.')).toEqual({ identityDocumentNumber: 'duplicateIdentity' })
    expect(profileServerError('Lỗi khác')).toEqual({})
  })
})

describe('đổi mật khẩu', () => {
  it('kiểm từng ô trước khi gửi', () => {
    expect(passwordErrors({ currentPassword: '', newPassword: '', confirmPassword: '' })).toEqual({ currentPassword: 'required', newPassword: 'required' })
    expect(passwordErrors({ currentPassword: 'cu-123', newPassword: 'abc', confirmPassword: 'abd' })).toEqual({ newPassword: 'passwordShort', confirmPassword: 'passwordMismatch' })
    expect(passwordErrors({ currentPassword: 'cu-123', newPassword: 'cu-123', confirmPassword: 'cu-123' })).toEqual({ newPassword: 'passwordSame' })
    expect(passwordErrors({ currentPassword: 'cu-123', newPassword: 'moi-123', confirmPassword: 'moi-123' })).toEqual({})
  })
  it('sai mật khẩu hiện tại thì lỗi nằm dưới ô đó', () => {
    expect(passwordServerError('Mật khẩu hiện tại không chính xác!')).toEqual({ currentPassword: 'passwordWrong' })
    expect(passwordServerError('khác')).toEqual({})
  })
})
