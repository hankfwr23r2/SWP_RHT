import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

// 1. Cấu trúc Response chuẩn từ Spring Boot (com.example.racehorse_transport.response.ApiResponse)
export interface ApiResponse<T = unknown> {
  status: 'success' | 'error'
  message: string
  data: T
}

// 2. Quản lý lưu trữ Access Token (ưu tiên an toàn và tiện lợi khi gọi API)
// App khách và app nội bộ chạy chung một trình duyệt: mỗi app giữ token riêng, nếu không đăng nhập Manager sẽ ghi đè token của khách
const tokenKey = () => `SWP_ACCESS_TOKEN_${typeof window !== 'undefined' && window.location.pathname.startsWith('/backoffice') ? 'backoffice' : 'customer'}`

export const tokenStorage = {
  get: (): string | null => (typeof localStorage !== 'undefined' ? localStorage.getItem(tokenKey()) : null),
  set: (token: string): void => {
    if (typeof localStorage !== 'undefined') localStorage.setItem(tokenKey(), token)
  },
  remove: (): void => {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(tokenKey())
  },
}

// 3. Khởi tạo Axios Instance
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || (typeof window !== 'undefined' ? '/api/v1' : 'http://localhost:8080/api/v1'),
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15000,
  // Cho phép gửi kèm cookie (cần thiết cho Refresh Token từ BE Spring Boot)
  withCredentials: true,
})

// 4. Request Interceptor: Tự động đính kèm Bearer Token vào Header
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = tokenStorage.get()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => {
    return Promise.reject(error)
  }
)

// 5. Response Interceptor: Xử lý dữ liệu trả về và bắt lỗi tập trung (Global Error Handling)
apiClient.interceptors.response.use(
  response => {
    // Trả về trực tiếp body data từ server (thường là ApiResponse<T>)
    return response
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response) {
      const { status, data } = error.response

      // Xử lý lỗi xác thực 401 Unauthorized (Token hết hạn hoặc không hợp lệ)
      if (status === 401) {
        tokenStorage.remove()
        // Chỉ chuyển hướng nếu không phải đang ở trang login
        if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
          console.warn('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.')
        }
      }

      // Lấy câu thông báo lỗi cụ thể từ Backend gửi lên nếu có
      const serverMessage = data?.message || 'Có lỗi xảy ra từ máy chủ.'
      return Promise.reject(new Error(serverMessage))
    }

    if (error.request) {
      return Promise.reject(new Error('Không thể kết nối đến máy chủ Backend (Network Error).'))
    }

    return Promise.reject(error)
  }
)

export default apiClient
