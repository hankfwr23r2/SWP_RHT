// Kiểm style trong file .tsx (AGENTS.md mục 8.5):
//   S1. Không dùng style={{...}}. Giá trị động không biểu diễn được bằng class (tọa độ, phần trăm, kích thước tính lúc chạy)
//       thì được dùng khi dòng ngay trên có comment "check-style: dynamic <lý do>".
//   S2. Không viết mã màu (#fff, #1e293b, rgb(...), hsl(...)). Dùng design token trong shared/styles/tokens.css.
// File có từ trước khi có kiểm tra này nằm trong EXCEPTIONS. Danh sách chỉ được rút ngắn:
// file trong danh sách mà đã hết vi phạm thì script báo lỗi để gỡ khỏi danh sách.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(FRONTEND, 'src')

const EXCEPTIONS = {
  S1: [
    'src/apps/backoffice/features/admin/accounts/AccountsPage.tsx',
    'src/apps/backoffice/features/admin/pricing/AdminPricingPage.tsx',
    'src/apps/backoffice/features/auth/StaffLoginPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/FleetPlanPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/InlineRouteMap.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/RouteMapPicker.tsx',
    'src/apps/backoffice/features/coordinator/fleet/FleetPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet/VehicleArt.tsx',
    'src/apps/backoffice/features/coordinator/incidents/CoordinatorIncidentsPage.tsx',
    'src/apps/backoffice/features/coordinator/incidents/IncidentPlanner.tsx',
    'src/apps/backoffice/features/coordinator/monitoring/MonitoringPage.tsx',
    'src/apps/backoffice/features/driver/DriverJob.tsx',
    'src/apps/backoffice/features/escort/EscortJob.tsx',
    'src/apps/backoffice/features/manager/approvals/ApprovalsPage.tsx',
    'src/apps/backoffice/features/manager/dashboard/AuditModal.tsx',
    'src/apps/backoffice/features/manager/dashboard/CancelTripModal.tsx',
    'src/apps/backoffice/features/manager/dashboard/DashboardPage.tsx',
    'src/apps/backoffice/features/manager/dashboard/OrderBoard.tsx',
    'src/apps/backoffice/features/manager/dashboard/OrderDetailModal.tsx',
    'src/apps/backoffice/features/manager/incidents/IncidentsPage.tsx',
    'src/apps/backoffice/features/manager/intake/IntakePage.tsx',
    'src/apps/backoffice/features/manager/reports/ReportCharts.tsx',
    'src/apps/backoffice/features/manager/reports/ReportsPage.tsx',
    'src/apps/backoffice/shared/BookingEvidence.tsx',
    'src/apps/backoffice/shared/BookingParts.tsx',
    'src/apps/backoffice/shared/HomeReturnPanel.tsx',
    'src/apps/backoffice/shared/HorseDocsModal.tsx',
    'src/apps/backoffice/shared/IncidentField.tsx',
    'src/apps/backoffice/shared/IncidentMap.tsx',
    'src/apps/backoffice/shared/ManifestView.tsx',
    'src/apps/backoffice/shared/ResourcePicker.tsx',
    'src/apps/backoffice/shared/StaffPicker.tsx',
    'src/apps/backoffice/shared/field.tsx',
    'src/apps/customer/features/auth/LoginPage.tsx',
    'src/apps/customer/features/auth/VerifyEmailPage.tsx',
    'src/apps/customer/features/home/HomePage.tsx',
    'src/apps/customer/features/policies/PolicyPage.tsx',
    'src/apps/customer/features/portal/PortalPage.tsx',
    'src/apps/customer/features/pricing/PriceLookup.tsx',
    'src/apps/customer/features/profile/ProfilePage.tsx',
    'src/shared/auth/ForgotPasswordPage.tsx',
    'src/shared/routing/RouteError.tsx',
    'src/shared/ui/CaptureEvidenceInfo.tsx',
    'src/shared/ui/CaptureScope.tsx',
    'src/shared/ui/ChangePasswordModal.tsx',
    'src/shared/ui/FileField.tsx',
    'src/shared/ui/Flag.tsx',
    'src/shared/ui/FormSelect.tsx',
    'src/shared/ui/ImageThumb.tsx',
    'src/shared/ui/QuoteSheet.tsx',
    'src/shared/ui/TripTrackMap.tsx',
  ],
  S2: [
    'src/apps/backoffice/features/admin/routes/AdminRoutesPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/FleetPlanPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/InlineRouteMap.tsx',
    'src/apps/backoffice/features/coordinator/fleet-plan/RouteMapPicker.tsx',
    'src/apps/backoffice/features/coordinator/fleet/FleetPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet/VehicleArt.tsx',
    'src/apps/backoffice/features/coordinator/incidents/IncidentPlanner.tsx',
    'src/apps/backoffice/features/driver/DriverJob.tsx',
    'src/apps/backoffice/features/escort/EscortJob.tsx',
    'src/apps/backoffice/features/manager/intake/IntakePage.tsx',
    'src/apps/backoffice/features/manager/reports/ReportCharts.tsx',
    'src/apps/backoffice/features/manager/reports/ReportsPage.tsx',
    'src/apps/backoffice/shared/BookingEvidence.tsx',
    'src/apps/backoffice/shared/HorseDocsModal.tsx',
    'src/apps/backoffice/shared/IncidentField.tsx',
    'src/apps/backoffice/shared/IncidentMap.tsx',
    'src/apps/backoffice/shared/InsurancePanel.tsx',
    'src/apps/backoffice/shared/ResourcePicker.tsx',
    'src/apps/backoffice/shared/StaffPicker.tsx',
    'src/apps/customer/features/profile/ProfilePage.tsx',
    'src/shared/ui/ChangePasswordModal.tsx',
    'src/shared/ui/FileField.tsx',
    'src/shared/ui/Flag.tsx',
    'src/shared/ui/ImageThumb.tsx',
    'src/shared/ui/TripTrackMap.tsx',
  ],
}

const INLINE_STYLE = /style=\{\{/
const DYNAMIC_MARKER = /check-style:\s*dynamic\s+\S/
// Mã hex 6 hoặc 8 ký tự; 3 hoặc 4 ký tự thì phải có chữ a-f, để không nhầm số đơn như "#123"
const COLOR = /#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|(?=[0-9]*[a-fA-F])[0-9a-fA-F]{3,4})\b|\b(?:rgba?|hsla?)\(/

function tsxFiles(dir) {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return tsxFiles(path)
    return name.endsWith('.tsx') ? [path] : []
  })
}

const violations = { S1: new Map(), S2: new Map() }
const report = (rule, file, line) => violations[rule].set(file, [...(violations[rule].get(file) ?? []), line])

for (const path of tsxFiles(SRC)) {
  const file = relative(FRONTEND, path).split(sep).join('/')
  const lines = readFileSync(path, 'utf8').split('\n')
  lines.forEach((text, index) => {
    if (INLINE_STYLE.test(text) && !DYNAMIC_MARKER.test(lines[index - 1] ?? '')) report('S1', file, index + 1)
    if (COLOR.test(text)) report('S2', file, index + 1)
  })
}

const MESSAGES = {
  S1: 'không dùng style={{...}}; dùng class trong CSS Module. Giá trị động thì thêm comment "check-style: dynamic <lý do>" ở dòng trên.',
  S2: 'không viết mã màu trong TSX; dùng design token (var(--color-...)) trong CSS Module.',
}

const errors = []
for (const rule of Object.keys(violations)) {
  const allowed = new Set(EXCEPTIONS[rule])
  for (const [file, lineNumbers] of violations[rule]) {
    if (!allowed.has(file)) errors.push(`${file}:${lineNumbers.join(',')}: ${rule} ${MESSAGES[rule]}`)
  }
  for (const file of allowed) {
    if (!violations[rule].has(file)) errors.push(`${file}: đã hết vi phạm ${rule}, gỡ khỏi EXCEPTIONS.${rule} trong scripts/check-style.mjs`)
  }
}

if (errors.length) {
  console.error(errors.join('\n'))
  console.error(`\ncheck-style: ${errors.length} lỗi (AGENTS.md mục 8.5)`)
  process.exit(1)
}
console.log('check-style: không có vi phạm mới')
