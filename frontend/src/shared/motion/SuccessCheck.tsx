// Dấu tích "vẽ" ra khi thao tác thành công (hiệu ứng kiểu Lottie, làm bằng SVG + CSS, không cần file animation).
import s from './SuccessCheck.module.css'

export function SuccessCheck({ size = 22 }: { size?: number }) {
  return (
    <svg className={s.check} width={size} height={size} viewBox="0 0 52 52" aria-hidden="true">
      <circle className={s.circle} cx="26" cy="26" r="24" fill="none" />
      <path className={s.tick} fill="none" d="M14 27 l8 8 l16 -17" />
    </svg>
  )
}
