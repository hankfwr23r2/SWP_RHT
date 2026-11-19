import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AuthProvider } from '../../src/shared/auth/AuthContext'
import { applyRuntimeBusinessConfig } from '../../src/shared/config/runtimeBusinessRules'
import { ToastProvider } from '../../src/shared/ui/toast'
import { ScheduleChangeCard } from '../../src/apps/customer/features/orders/ScheduleChangeCard'
import AdminRulesPage from '../../src/apps/backoffice/features/admin/rules/AdminRulesPage'
import { QuoteSheet } from '../../src/shared/ui/QuoteSheet'
import { toBooking } from '../../src/shared/services/bookingMapper'
import type { SystemConfigItem } from '../../src/shared/api/systemConfig'
import '../../src/shared/i18n'
import '../../src/shared/styles/base.css'

// Trang thử chỉ nằm trong test: dựng đúng màn khách và màn admin, nạp tham số như lúc app khởi động.
const view = new URLSearchParams(location.search).get('view')
const numbers = {
  DEPOSIT_RATE: '0.30', QUOTE_VALID_HOURS: '24', MAX_CONTINUOUS_HOURS: '4',
  MIN_REST_MINUTES: '30', VITAL_CHECK_INTERVAL_HOURS: '2', MIN_LEAD_DAYS: '20',
}
const contact = { HOTLINE: '1900 7777', OFFICE_ADDRESS: '25 Nguyễn Huệ, Quận 1, TP.HCM' }
applyRuntimeBusinessConfig({ ...numbers, ...contact })

const item = (configKey: string, configName: string, configValue: string, configGroup: SystemConfigItem['configGroup'], unit = ''): SystemConfigItem =>
  ({ configKey, configName, configValue, configGroup, unit, description: configName } as SystemConfigItem)
const configs = [
  item('DEPOSIT_RATE', 'Tỷ lệ đặt cọc', '0.30', 'PAYMENT', 'tỷ lệ'),
  item('QUOTE_VALID_HOURS', 'Thời hạn hiệu lực báo giá', '24', 'PAYMENT', 'giờ'),
  item('MAX_CONTINUOUS_HOURS', 'Thời gian di chuyển liên tục tối đa', '4', 'WELFARE', 'giờ'),
  item('MIN_REST_MINUTES', 'Thời gian nghỉ tối thiểu', '30', 'WELFARE', 'phút'),
  item('VITAL_CHECK_INTERVAL_HOURS', 'Tần suất đo sinh hiệu', '2', 'WELFARE', 'giờ'),
  item('MIN_LEAD_DAYS', 'Số ngày đặt trước tối thiểu', '20', 'OPERATIONS', 'ngày'),
  item('HOTLINE', 'Số hotline', '1900 7777', 'CONTACT' as SystemConfigItem['configGroup']),
  item('OFFICE_ADDRESS', 'Địa chỉ văn phòng', '25 Nguyễn Huệ, Quận 1, TP.HCM', 'CONTACT' as SystemConfigItem['configGroup']),
]

const booking = toBooking({
  id: 'BK-7F3K9QXM', type: 'domestic', customer: 'Customer', customerName: 'Customer',
  status: 'schedule_change_required', createdAt: 1000, departAt: Date.parse('2026-11-30T00:00:00+07:00'),
  originName: 'Hà Nội', destName: 'Đà Nẵng', totalHorses: 0, horses: [], consignor: { name: 'Sender', phone: '' },
  consignee: { name: 'Recipient', phone: '' }, scheduleChange: { flaggedAt: 1000, previousStatus: 'pending_intake' },
})

const router = createBrowserRouter([{
  path: '/contact-test',
  loader: () => ({ configs, errors: [] }),
  element: view === 'admin' ? <AdminRulesPage />
    : view === 'quote' ? <QuoteSheet lines={[{ label: 'Cước vận chuyển nguyên chuyến', detail: '', amount: 5_000_000 }]} subtotal={5_000_000} total={5_000_000} deposit={1_500_000} balance={3_500_000} expiresAt={Date.now() + 86_400_000} />
    : <main style={{ maxWidth: 650, margin: '30px auto', padding: 16 }}><ScheduleChangeCard b={booking} onDone={() => {}} /></main>,
}])
createRoot(document.getElementById('root')!).render(<AuthProvider app="backoffice"><ToastProvider><RouterProvider router={router} /></ToastProvider></AuthProvider>)
