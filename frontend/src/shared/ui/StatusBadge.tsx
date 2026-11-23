import { Badge, type BadgeTone } from './Badge'

export type StatusTone = Exclude<BadgeTone, 'primary'>

const ICONS: Record<StatusTone, string> = {
  success: 'fa-circle-check',
  warning: 'fa-triangle-exclamation',
  danger: 'fa-circle-xmark',
  info: 'fa-circle-info',
  neutral: 'fa-circle',
}

interface StatusBadgeProps {
  tone: StatusTone
  label: string
}

// Nơi duy nhất gán màu cho sắc thái trạng thái (AGENTS.md mục 8.5). Luôn có chữ và biểu tượng,
// không truyền đạt chỉ bằng màu (mục 8.7). Bảng "trạng thái nghiệp vụ → sắc thái" đặt ở shared/components
// của từng miền, không đặt ở đây vì shared/ui không biết nghiệp vụ.
export function StatusBadge({ tone, label }: StatusBadgeProps) {
  return <Badge tone={tone} icon={<i className={`fa-solid ${ICONS[tone]}`} />}>{label}</Badge>
}
