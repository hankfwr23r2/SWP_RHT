import { useTranslation } from 'react-i18next'
import s from './Skeleton.module.css'

interface SkeletonProps {
  // text: các dòng chữ; title: tiêu đề; block: khối lấp đầy chiều cao vùng chứa; circle: ảnh đại diện
  variant?: 'text' | 'title' | 'block' | 'circle'
  lines?: number
  label?: string
}

// Trạng thái đang tải giữ đúng bố cục, để trang không nhảy khi có dữ liệu (AGENTS.md mục 8.6)
export function Skeleton({ variant = 'text', lines = 1, label }: SkeletonProps) {
  const { t } = useTranslation()
  const count = variant === 'text' ? Math.max(1, lines) : 1
  return (
    <div className={s.skeleton} role="status">
      <span className={s.srOnly}>{label ?? t('ui.loading')}</span>
      <div className={s.bones} aria-hidden="true">
        {Array.from({ length: count }, (_, index) => <span key={index} className={`${s.bone} ${s[variant]}`} />)}
      </div>
    </div>
  )
}
