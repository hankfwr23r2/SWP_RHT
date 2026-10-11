// Đoạn văn dài: chỉ hiện câu đầu, phần còn lại cất sau nút "Xem thêm" để trang gọn mắt.
import { useState } from 'react'
import { splitLead } from '../lib/text'
import s from './ReadMore.module.css'

export function ReadMore({ text, className }: { text: string; className?: string }) {
  const [open, setOpen] = useState(false)
  const { lead, rest } = splitLead(text)
  return (
    <p className={className}>
      {lead}
      {rest && open && <> {rest}</>}
      {rest && <button type="button" className={s.more} aria-expanded={open} onClick={() => setOpen(o => !o)}>{open ? 'Thu gọn' : 'Xem thêm'}</button>}
    </p>
  )
}
