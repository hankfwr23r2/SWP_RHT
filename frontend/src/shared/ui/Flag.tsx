// Cờ quốc gia vẽ bằng SVG (thay emoji cờ, vốn hiển thị khác nhau hoặc thành ô vuông trên nhiều máy).
import type { CountryCode } from '../config/network'
import { FLAG_SVG } from './flags'

export function Flag({ code, size = 18 }: { code: CountryCode; size?: number }) {
  return (
    <span
      aria-hidden="true"
      style={{ display: 'inline-block', width: size, height: size, borderRadius: '50%', overflow: 'hidden', verticalAlign: 'middle', boxShadow: '0 0 0 1px rgba(15,23,42,0.12)', flexShrink: 0 }}
      dangerouslySetInnerHTML={{ __html: FLAG_SVG[code].replace('<svg ', '<svg width="100%" height="100%" ') }}
    />
  )
}
