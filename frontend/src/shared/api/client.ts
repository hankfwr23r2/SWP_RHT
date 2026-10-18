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

interface RetryQueueItem {
  resolve: (token: string) => void
  reject: (err: unknown) => void
}

let isRefreshing = false
let failedQueue: RetryQueueItem[] = []

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else if (token) {
      prom.resolve(token)
    }
  })
  failedQueue = []
}

// 5. Response Interceptor: Xử lý dữ liệu trả về, Silent Refresh và bắt lỗi tập trung
apiClient.interceptors.response.use(
  response => {
    // Trả về trực tiếp body data từ server (thường là ApiResponse<T>)
    return response
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined

    if (error.response) {
      const { status, data } = error.response
      const requestUrl = originalRequest?.url || ''

      // Kiểm tra xem có phải các API auth cơ bản không (để tránh lặp vô tận)
      const isAuthRequest =
        requestUrl.includes('/auth/login') ||
        requestUrl.includes('/auth/customer/login') ||
        requestUrl.includes('/auth/staff/login') ||
        requestUrl.includes('/auth/refresh-token')

      // Nếu gặp lỗi 401 Unauthorized và chưa từng thử retry: Tự động refresh token ngầm
      if (status === 401 && !isAuthRequest && originalRequest && !originalRequest._retry) {
        if (isRefreshing) {
          // Nếu đã có 1 request khác đang kích hoạt refresh, đưa request này vào hàng đợi chờ token mới
          try {
            const token = await new Promise<string>((resolve, reject) => {
              failedQueue.push({ resolve, reject })
            })
            originalRequest.headers.Authorization = `Bearer ${token}`
            return apiClient(originalRequest)
          } catch (err) {
            return Promise.reject(err)
          }
        }

        originalRequest._retry = true
        isRefreshing = true

        try {
          // Gọi API làm mới access token từ HttpOnly Cookie
          const refreshRes = await axios.post<ApiResponse<{ accessToken: string }>>(
            `${apiClient.defaults.baseURL}/auth/refresh-token`,
            {},
            { withCredentials: true }
          )

          const newAccessToken = refreshRes.data?.data?.accessToken
          if (!newAccessToken) {
            throw new Error('Không nhận được Access Token mới từ máy chủ.')
          }

          // Cập nhật token mới vào storage và header mặc định
          tokenStorage.set(newAccessToken)
          apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`

          // Giải phóng toàn bộ các request đang chờ trong hàng đợi
          processQueue(null, newAccessToken)

          // Thực hiện lại request ban đầu với token mới
          return apiClient(originalRequest)
        } catch (refreshErr) {
          processQueue(refreshErr, null)
          tokenStorage.remove()
          if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
            console.warn('Phiên đăng nhập đã hết hạn hoàn toàn. Vui lòng đăng nhập lại.')
          }
          return Promise.reject(new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'))
        } finally {
          isRefreshing = false
        }
      }

      // Nếu 401 sau khi đã retry hoặc từ chính API auth thì xóa token
      if (status === 401) {
        tokenStorage.remove()
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

