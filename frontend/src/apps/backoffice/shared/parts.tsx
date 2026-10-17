// Thành phần dùng chung các trang nội bộ: ô tìm kiếm, hàm nối class cx, style chung.
import s from './parts.module.css'

export const cx = (...c: (string | false | undefined | null)[]) => c.filter(Boolean).join(' ')

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return <div className={s.search}><i className="fa-solid fa-search" /><input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} /></div>
}

export { s as partStyles }

