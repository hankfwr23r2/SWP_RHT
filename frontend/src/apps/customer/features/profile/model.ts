// Chuyển đổi giữa hồ sơ khách backend trả về và biểu mẫu; kiểm tra sớm trước khi gửi, backend vẫn kiểm lại
import type { CustomerProfile, CustomerProfileUpdate } from '@shared/api/bookingOrders'

type AsText<T> = { [K in keyof T]-?: string }
export type ProfileForm = Omit<AsText<CustomerProfileUpdate>, 'customerType' | 'identityDocumentType'> & {
  customerType: CustomerProfileUpdate['customerType']
  identityDocumentType: NonNullable<CustomerProfileUpdate['identityDocumentType']> | ''
}
export type ProfileErrors = Partial<Record<keyof ProfileForm, string>>

export const CUSTOMER_TYPES = ['PERSON', 'ORGANIZATION'] as const
export const IDENTITY_TYPES = ['CCCD', 'PASSPORT', 'BUSINESS_REG'] as const

export const profileForm = (profile: CustomerProfile): ProfileForm => ({
  customerType: profile.customerType ?? 'PERSON',
  fullName: profile.fullName ?? '',
  phone: profile.phone ?? '',
  address: profile.address ?? '',
  countryCode: profile.countryCode ?? '',
  identityDocumentType: profile.identityDocumentType ?? '',
  identityDocumentNumber: profile.identityDocumentNumber ?? '',
  identityDocumentCountryCode: profile.identityDocumentCountryCode ?? '',
  taxCode: profile.taxCode ?? '',
})

// Đổi loại giấy tờ: bỏ trống thì xóa cả số và nước cấp; chọn lần đầu thì nước cấp lấy theo quốc gia của khách
export function withIdentityType(form: ProfileForm, value: string): ProfileForm {
  const type = IDENTITY_TYPES.find(item => item === value) ?? ''
  return type
    ? { ...form, identityDocumentType: type, identityDocumentCountryCode: form.identityDocumentCountryCode || form.countryCode }
    : { ...form, identityDocumentType: '', identityDocumentNumber: '', identityDocumentCountryCode: '' }
}

// Ô trống gửi undefined để backend lưu là chưa có
const optional = (value: string) => value.trim() || undefined

export const profileRequest = (form: ProfileForm): CustomerProfileUpdate => ({
  customerType: form.customerType,
  fullName: form.fullName.trim(),
  phone: optional(form.phone),
  address: optional(form.address),
  countryCode: form.countryCode,
  identityDocumentType: form.identityDocumentType || undefined,
  identityDocumentNumber: form.identityDocumentType ? optional(form.identityDocumentNumber) : undefined,
  identityDocumentCountryCode: form.identityDocumentType ? optional(form.identityDocumentCountryCode) : undefined,
  taxCode: optional(form.taxCode),
})

// Trả về khóa chữ của lỗi từng ô (profile.errors.*)
export function profileErrors(form: ProfileForm): ProfileErrors {
  const errors: ProfileErrors = {}
  if (!form.fullName.trim()) errors.fullName = 'required'
  if (!form.countryCode) errors.countryCode = 'required'
  if (form.identityDocumentType) {
    if (!form.identityDocumentNumber.trim()) errors.identityDocumentNumber = 'identityIncomplete'
    if (!form.identityDocumentCountryCode) errors.identityDocumentCountryCode = 'identityIncomplete'
  }
  return errors
}

export interface PasswordForm { currentPassword: string; newPassword: string; confirmPassword: string }
export type PasswordErrors = Partial<Record<keyof PasswordForm, string>>
export const EMPTY_PASSWORD: PasswordForm = { currentPassword: '', newPassword: '', confirmPassword: '' }

// Độ dài theo ChangePasswordRequest trong OpenAPI
export const PASSWORD_MIN = 6
export const PASSWORD_MAX = 100

export function passwordErrors(form: PasswordForm): PasswordErrors {
  const errors: PasswordErrors = {}
  if (!form.currentPassword) errors.currentPassword = 'required'
  if (form.newPassword.length < PASSWORD_MIN) errors.newPassword = form.newPassword ? 'passwordShort' : 'required'
  else if (form.newPassword === form.currentPassword) errors.newPassword = 'passwordSame'
  if (form.confirmPassword !== form.newPassword) errors.confirmPassword = 'passwordMismatch'
  return errors
}

// Lỗi nghiệp vụ của backend không kèm tên ô: gắn vào ô khách cần sửa
const byMessage = <F extends string>(messages: Record<string, [F, string]>) => (message: string) => {
  const known = messages[message]
  return (known ? { [known[0]]: known[1] } : {}) as Partial<Record<F, string>>
}

export const profileServerError = byMessage<keyof ProfileForm>({
  'Giấy tờ này đã được đăng ký.': ['identityDocumentNumber', 'duplicateIdentity'],
  'Vui lòng nhập đủ loại, số và quốc gia cấp giấy tờ.': ['identityDocumentNumber', 'identityIncomplete'],
  'Quốc gia không hợp lệ.': ['countryCode', 'invalidCountry'],
})

export const passwordServerError = byMessage<keyof PasswordForm>({
  'Mật khẩu hiện tại không chính xác!': ['currentPassword', 'passwordWrong'],
  'Xác nhận mật khẩu mới không trùng khớp!': ['confirmPassword', 'passwordMismatch'],
  'Mật khẩu mới không được trùng với mật khẩu hiện tại!': ['newPassword', 'passwordSame'],
})
