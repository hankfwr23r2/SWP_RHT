import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'

// 1. Cấu trúc Response chuẩn từ Spring Boot (com.example.racehorse_transport.response.ApiResponse)
export interface ApiResponse<T = unknown> {
  status: 'success' | 'error'
  message: string
  data: T
}

const refreshBeforeExpirationMs = 60_000
let refreshTimer: ReturnType<typeof setTimeout> | undefined
let refreshPromise: Promise<string> | null = null

function getTokenExpiration(token: string): number | null {
  try {
    const payload = token.split('.')[1]
    if (!payload) return null

    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const binary = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='))
    const bytes = Uint8Array.from(binary, character => character.charCodeAt(0))
    const decoded = new TextDecoder().decode(bytes)
    const claims = JSON.parse(decoded) as { exp?: unknown }
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null
  } catch (error) {
    console.warn('Không thể đọc thời hạn Access Token; sẽ refresh khi API trả về 401.', error)
    return null
  }
}

function scheduleTokenRefresh(token: string): void {
  if (refreshTimer) clearTimeout(refreshTimer)
  refreshTimer = undefined

  if (typeof window === 'undefined') return

  const expirationTime = getTokenExpiration(token)
  if (expirationTime === null) return

  const delay = Math.max(0, expirationTime - Date.now() - refreshBeforeExpirationMs)
  refreshTimer = setTimeout(() => {
    void refreshAccessToken().catch(error => {
      console.warn('Không thể tự làm mới Access Token; sẽ thử lại khi API cần xác thực.', error)
    })
  }, delay)
}

// 2. Quản lý lưu trữ Access Token (ưu tiên an toàn và tiện lợi khi gọi API)
// App khách và app nội bộ chạy chung một trình duyệt: mỗi app giữ token riêng, nếu không đăng nhập Manager sẽ ghi đè token của khách
const tokenKey = () => `SWP_ACCESS_TOKEN_${typeof window !== 'undefined' && window.location.pathname.startsWith('/backoffice') ? 'backoffice' : 'customer'}`

export const tokenStorage = {
  get: (): string | null => (typeof localStorage !== 'undefined' ? localStorage.getItem(tokenKey()) : null),
  set: (token: string): void => {
    if (typeof localStorage !== 'undefined') localStorage.setItem(tokenKey(), token)
    scheduleTokenRefresh(token)
  },
  remove: (): void => {
    if (typeof localStorage !== 'undefined') localStorage.removeItem(tokenKey())
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = undefined
    clearDefaultAuthorization()
  },
}

function clearDefaultAuthorization(): void {
  delete apiClient.defaults.headers.common.Authorization
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

function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = axios.post<ApiResponse<{ accessToken: string }>>(
      `${apiClient.defaults.baseURL}/auth/refresh-token`,
      {},
      { withCredentials: true }
    ).then(refreshRes => {
      const newAccessToken = refreshRes.data?.data?.accessToken
      if (!newAccessToken) {
        throw new Error('Không nhận được Access Token mới từ máy chủ.')
      }

      tokenStorage.set(newAccessToken)
      apiClient.defaults.headers.common.Authorization = `Bearer ${newAccessToken}`
      return newAccessToken
    }).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
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
        originalRequest._retry = true

        try {
          const newAccessToken = await refreshAccessToken()
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`

          // Thực hiện lại request ban đầu với token mới
          return apiClient(originalRequest)
        } catch {
          tokenStorage.remove()
          if (typeof window !== 'undefined' && !window.location.pathname.includes('/login')) {
            console.warn('Phiên đăng nhập đã hết hạn hoàn toàn. Vui lòng đăng nhập lại.')
          }
          return Promise.reject(new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'))
        }
      }

      // Nếu 401 sau khi đã retry hoặc từ chính API auth thì xóa token
      if (status === 401) {
        tokenStorage.remove()
      }

      // Lấy câu thông báo lỗi cụ thể từ Backend gửi lên nếu có
      let serverMessage = data?.message || 'Có lỗi xảy ra từ máy chủ.'
      if (data?.data && typeof data.data === 'object' && !Array.isArray(data.data)) {
        const details = Object.entries(data.data)
          .map(([, val]) => (typeof val === 'string' ? val : JSON.stringify(val)))
          .filter(Boolean)
        if (details.length > 0) {
          serverMessage = `${serverMessage}: ${details.join('; ')}`
        }
      }
      // Kèm mã HTTP để form phân biệt lỗi (vd. 409: email đã có tài khoản)
      return Promise.reject(Object.assign(new Error(serverMessage), { status }))
    }

    if (error.request) {
      return Promise.reject(new Error('Không kết nối được máy chủ. Vui lòng kiểm tra mạng rồi thử lại.'))
    }

    return Promise.reject(error)
  }
)

const storedAccessToken = tokenStorage.get()
if (storedAccessToken) scheduleTokenRefresh(storedAccessToken)

export default apiClient
