import apiClient, { type ApiResponse } from './client'

// DTO gửi lên cho Đăng nhập
export interface LoginPayload {
  username: string
  password: string
}

// DTO gửi lên cho Đăng ký Khách hàng
export interface RegisterPayload {
  username: string
  email: string
  password: string
  fullName?: string
  phone?: string
  address?: string
  language?: 'vi' | 'en' // ngôn ngữ đang chọn trên giao diện, để mail gửi đúng ngôn ngữ
}

// DTO nhận về từ Spring Boot (com.example.racehorse_transport.dto.AuthResponse)
export interface AuthResponseData {
  accessToken: string
  tokenType: string
  userId: number
  username: string
  email: string
  role: string
  fullName?: string
  staffCode?: string
  // Khách chưa xác thực email: không có accessToken, đã gửi OTP về email
  emailVerificationRequired?: boolean
}

// DTO gửi lên để Đổi mật khẩu cá nhân
export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ResetPasswordWithOtpPayload {
  email: string
  otp: string
  newPassword: string
  confirmPassword: string
}

const captchaConfig = (captchaToken: string) => ({
  headers: { 'X-Captcha-Token': captchaToken },
})

export const authApi = {
  // 1. Đăng nhập Khách hàng -> POST /api/v1/auth/customer/login
  loginCustomer: async (payload: LoginPayload, captchaToken: string): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/customer/login', payload, captchaConfig(captchaToken))
    return res.data.data
  },

  // 2. Đăng nhập Nhân viên nội bộ -> POST /api/v1/auth/staff/login
  loginStaff: async (payload: LoginPayload, captchaToken: string): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/staff/login', payload, captchaConfig(captchaToken))
    return res.data.data
  },

  // 3. Đăng ký Khách hàng -> POST /api/v1/auth/register
  register: async (payload: RegisterPayload, captchaToken: string): Promise<string> => {
    const res = await apiClient.post<ApiResponse<string>>('/auth/register', payload, captchaConfig(captchaToken))
    return res.data.data
  },

  // 4. Làm mới Access Token -> POST /api/v1/auth/refresh-token (gửi kèm HttpOnly cookie)
  refreshToken: async (): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/refresh-token')
    return res.data.data
  },

  // 5. Đăng xuất -> POST /api/v1/auth/logout (xóa HttpOnly cookie)
  logout: async (): Promise<void> => {
    await apiClient.post('/auth/logout')
  },

  // 6. Đổi mật khẩu cá nhân -> POST /api/v1/auth/change-password
  changePassword: async (payload: ChangePasswordPayload): Promise<void> => {
    await apiClient.post('/auth/change-password', payload)
  },

  requestPasswordResetOtp: async (email: string, captchaToken: string): Promise<string> => {
    const res = await apiClient.post<ApiResponse<void>>('/auth/password-reset/request-otp', { email }, captchaConfig(captchaToken))
    return res.data.message
  },

  resendEmailOtp: async (email: string, captchaToken: string): Promise<string> => {
    const res = await apiClient.post<ApiResponse<void>>('/auth/email-verification/resend', { email }, captchaConfig(captchaToken))
    return res.data.message
  },

  confirmEmail: async (email: string, otp: string, captchaToken: string): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/email-verification/confirm', { email, otp }, captchaConfig(captchaToken))
    return res.data.data
  },

  resetPasswordWithOtp: async (payload: ResetPasswordWithOtpPayload, captchaToken: string): Promise<string> => {
    const res = await apiClient.post<ApiResponse<void>>('/auth/password-reset/complete', payload, captchaConfig(captchaToken))
    return res.data.message
  },
}
