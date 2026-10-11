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
}

// DTO gửi lên để Đổi mật khẩu cá nhân
export interface ChangePasswordPayload {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export const authApi = {
  // 1. Đăng nhập Khách hàng -> POST /api/v1/auth/customer/login
  loginCustomer: async (payload: LoginPayload): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/customer/login', payload)
    return res.data.data
  },

  // 2. Đăng nhập Nhân viên nội bộ -> POST /api/v1/auth/staff/login
  loginStaff: async (payload: LoginPayload): Promise<AuthResponseData> => {
    const res = await apiClient.post<ApiResponse<AuthResponseData>>('/auth/staff/login', payload)
    return res.data.data
  },

  // 3. Đăng ký Khách hàng -> POST /api/v1/auth/register
  register: async (payload: RegisterPayload): Promise<string> => {
    const res = await apiClient.post<ApiResponse<string>>('/auth/register', payload)
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
}

