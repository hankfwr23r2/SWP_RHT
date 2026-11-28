import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import s from './Button.module.css'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg'

type BaseProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  variant?: ButtonVariant
  size?: ButtonSize
  // Đang xử lý: khóa nút và hiện vòng quay để không bấm hai lần (AGENTS.md mục 8.6)
  loading?: boolean
  icon?: ReactNode
  fullWidth?: boolean
}

// Nút chỉ có biểu tượng bắt buộc có aria-label (AGENTS.md mục 8.7)
export type ButtonProps = BaseProps & ({ children: ReactNode } | { children?: undefined; 'aria-label': string })

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  icon,
  fullWidth = false,
  type = 'button',
  disabled,
  className,
  children,
  ...rest
}: ButtonProps) {
  const { t } = useTranslation()
  const classes = [s.button, s[variant], s[size], fullWidth && s.full, children === undefined && s.iconOnly, className]
    .filter(Boolean)
    .join(' ')
  return (
    <button {...rest} type={type} className={classes} disabled={disabled || loading} aria-busy={loading || undefined}>
      {loading ? <span className={s.spinner} aria-hidden="true" /> : icon && <span className={s.icon} aria-hidden="true">{icon}</span>}
      {children}
      {loading && <span className={s.srOnly}>{t('ui.processing')}</span>}
    </button>
  )
}
