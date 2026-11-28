import { useTranslation } from 'react-i18next'
import s from './Stepper.module.css'

interface StepperProps {
  // Tên luồng cho trình đọc màn hình, ví dụ "Các bước đặt đơn"
  label: string
  steps: string[]
  // Bước đang làm, đếm từ 0
  current: number
}

// Chỉ hiển thị tiến độ của luồng nhiều bước (AGENTS.md mục 8.6). Điện thoại chỉ hiện "Bước 2/4: tên bước".
export function Stepper({ label, steps, current }: StepperProps) {
  const { t } = useTranslation()
  return (
    <nav aria-label={label} className={s.stepper}>
      <p className={s.compact} aria-hidden="true">
        {t('ui.step', { current: current + 1, total: steps.length })}: <strong>{steps[current]}</strong>
      </p>
      <ol className={s.list}>
        {steps.map((name, i) => {
          const done = i < current
          return (
            <li key={name} className={[s.step, done && s.done, i === current && s.current].filter(Boolean).join(' ')} aria-current={i === current ? 'step' : undefined}>
              <span className={s.marker} aria-hidden="true">{done ? <i className="fa-solid fa-check" /> : i + 1}</span>
              <span className={s.name}>{name}</span>
              {done && <span className={s.srOnly}> ({t('ui.stepDone')})</span>}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
