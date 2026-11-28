import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import s from './State.module.css'

interface ErrorStateProps {
  title?: ReactNode
  // Lời giải thích bằng ngôn ngữ người dùng, không hiện mã lỗi kỹ thuật (AGENTS.md mục 8.6)
  message?: ReactNode
  onRetry: () => void
  retrying?: boolean
}

export function ErrorState({ title, message, onRetry, retrying = false }: ErrorStateProps) {
  const { t } = useTranslation()
  return (
    <div className={s.state} role="alert">
      <div className={`${s.icon} ${s.danger}`} aria-hidden="true"><i className="fa-solid fa-circle-exclamation" /></div>
      <h2 className={s.title}>{title ?? t('ui.errorTitle')}</h2>
      {message && <p className={s.description}>{message}</p>}
      <div className={s.action}>
        <Button variant="primary" onClick={onRetry} loading={retrying}>{t('ui.retry')}</Button>
      </div>
    </div>
  )
}
