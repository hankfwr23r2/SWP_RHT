// Lưu phiên khách sau khi đăng nhập (bằng mật khẩu hoặc OTP email) rồi chuyển trang.
import { useNavigate } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import type { AuthResponseData } from '@shared/api/auth'
import { tokenStorage } from '@shared/api/client'

export function useCustomerSignIn() {
  const { login } = useAuth()
  const navigate = useNavigate()
  return async (res: AuthResponseData, from?: string) => {
    tokenStorage.set(res.accessToken)
    login({
      role: 'customer',
      name: res.fullName || res.username,
      username: res.email || res.username,
    })
    // Chờ router tải xong trang đích (kể cả dữ liệu) rồi mới thôi trạng thái đang đăng nhập
    await navigate(from ?? '/portal')
  }
}
