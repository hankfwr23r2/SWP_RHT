import { useEffect, type ReactNode } from 'react'
import { systemConfigApi } from '@shared/api/systemConfig'
import { applyRuntimeBusinessConfig } from './runtimeBusinessRules'

const CACHE_KEY = 'cached_public_system_configs'

// Nạp cache từ localStorage ngay khi khởi động để áp dụng tức thì trước lần render đầu tiên
function initCachedConfig() {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Record<string, string>
      applyRuntimeBusinessConfig(parsed)
    }
  } catch {
    // Bỏ qua lỗi đọc cache, dùng giá trị mặc định có sẵn trong file rules
  }
}

initCachedConfig()

export function BusinessConfigGate({ children }: { children: ReactNode }) {
  useEffect(() => {
    let active = true
    // Tải cấu hình mới nhất từ backend dưới nền (non-blocking)
    systemConfigApi.listPublicConfigs()
      .then(configs => {
        if (!active) return
        const map = Object.fromEntries(configs.map(c => [c.configKey, c.configValue]))
        try {
          applyRuntimeBusinessConfig(map)
          localStorage.setItem(CACHE_KEY, JSON.stringify(map))
        } catch (e) {
          console.warn('Lỗi áp dụng cấu hình nghiệp vụ mới từ backend:', e)
        }
      })
      .catch(error => {
        // Backend đang cold start hoặc offline: không chặn giao diện, tiếp tục dùng cache/fallback mặc định
        console.warn('Không thể làm mới cấu hình nghiệp vụ từ backend, sử dụng cấu hình hiện tại:', error)
      })
    return () => { active = false }
  }, [])

  return <>{children}</>
}

