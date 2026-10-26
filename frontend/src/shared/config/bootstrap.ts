// Dữ liệu dùng chung khi mở app: cấu hình nghiệp vụ, danh mục địa điểm, giấy tờ bắt buộc, tải một lần qua /public/bootstrap.
// Không chặn trang: trang chủ, đăng nhập… hiện ngay; trang dùng danh mục thì router chờ qua loader (buildRouter).
import apiClient, { type ApiResponse } from '@shared/api/client'
import type { DocumentRequirement } from '@shared/api/documentRequirements'
import type { SystemConfigItem } from '@shared/api/systemConfig'
import { setNetworkLocations, type CatalogLocation } from './network'
import { applyDocumentRequirements } from './runtimeDocumentRequirements'
import { applyRuntimeBusinessConfig } from './runtimeBusinessRules'

interface BootstrapPayload { configs: SystemConfigItem[]; locations: CatalogLocation[]; documentRequirements: DocumentRequirement[] }

const CONFIG_CACHE_KEY = 'cached_public_system_configs'

// Cấu hình lần trước (nếu có) áp dụng ngay để trang hiện trước khi request trả về dùng đúng số admin đã đặt
try {
  const raw = localStorage.getItem(CONFIG_CACHE_KEY)
  if (raw) applyRuntimeBusinessConfig(JSON.parse(raw) as Record<string, string>)
} catch {
  // Cache hỏng: dùng giá trị mặc định trong file rules
}

let pending: Promise<void> | null = null

async function fetchBootstrap() {
  const { data } = await apiClient.get<ApiResponse<BootstrapPayload>>('/public/bootstrap')
  const payload = data.data
  if (!payload.locations.length) throw new Error('Danh mục địa điểm chưa có dữ liệu hoạt động.')
  const configs = Object.fromEntries(payload.configs.map(c => [c.configKey, c.configValue]))
  applyRuntimeBusinessConfig(configs)
  setNetworkLocations(payload.locations)
  applyDocumentRequirements(payload.documentRequirements)
  try { localStorage.setItem(CONFIG_CACHE_KEY, JSON.stringify(configs)) } catch { /* không lưu được cache thì thôi */ }
}

// Gọi bao nhiêu lần cũng chỉ một request; lỗi thì lần gọi sau thử lại
export function loadBootstrap(): Promise<void> {
  pending ??= fetchBootstrap().catch(error => {
    pending = null
    throw error
  })
  return pending
}
