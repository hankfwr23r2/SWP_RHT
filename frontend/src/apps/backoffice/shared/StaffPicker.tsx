// Manager chọn Kiểm dịch viên / Điều phối viên giao việc: popup dạng thẻ giống chọn xe, tài xế của Điều phối viên.
// Chỉ cho biết người đó đang hoạt động hay đang nghỉ (nghỉ thì khóa kèm lý do), không hiện số đơn họ đang làm.
import { useState } from 'react'
import { formatDate } from '@shared/lib/format'
import type { StaffMember } from '@shared/types/staff'
import { Modal } from '@shared/ui/Modal'
import p from './ResourcePicker.module.css'

type Task = 'specialist' | 'coordinator'
const TEXT: Record<Task, { title: string; sub: string; search: string }> = {
  specialist: { title: 'Chọn Kiểm dịch viên', sub: 'Phụ trách thẩm định hồ sơ ngựa và làm giấy kiểm dịch, hải quan.', search: 'Tìm tên, mã, số điện thoại' },
  coordinator: { title: 'Chọn Điều phối viên', sub: 'Phụ trách chọn xe và lập lộ trình.', search: 'Tìm tên, mã, số điện thoại' },
}
const initials = (name: string) => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
const hue = (id: string) => [...id].reduce((n, c) => n + c.charCodeAt(0) * 7, 0) % 360

export function StaffPicker({ task, staff, selected, onPick, onClose }: {
  task: Task; staff: StaffMember[]; selected: string; onPick: (id: string) => void; onClose: () => void
}) {
  const [text, setText] = useState('')
  const [shake, setShake] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const t = TEXT[task]
  const rows = staff.filter(s => s.role === (task === 'specialist' ? 'inspector' : 'coordinator')).map(s => ({ s, off: s.status === 'off' }))
    .sort((a, z) => Number(a.off) - Number(z.off) || a.s.id.localeCompare(z.s.id))
  const kw = text.trim().toLowerCase()
  const shown = rows.filter(r => !kw || [r.s.name, r.s.id, r.s.phone].some(x => x.toLowerCase().includes(kw)))
  const press = (r: (typeof rows)[number]) => {
    if (r.off) { setShake(r.s.id); setNotice(`${r.s.name} đang nghỉ${r.s.offReason ? `: ${r.s.offReason}` : ''}${r.s.offTo ? `, đến ${formatDate(r.s.offTo)}` : ''}. Không giao việc được.`); setTimeout(() => setShake(null), 480); return }
    onPick(r.s.id); onClose()
  }
  return (
    <Modal wide onClose={onClose} title={t.title} subtitle={t.sub}>
      <div className={p.bar}>
        <label className={p.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t.search} value={text} onChange={e => setText(e.target.value)} /></label>
        <span className={p.summary}><b>{rows.filter(r => !r.off).length}</b>/{rows.length} đang làm việc</span>
      </div>
      {notice && <div key={notice} className={p.notice} role="status"><i className="fa-solid fa-lock" /> {notice}</div>}
      <div className={p.grid}>
        {shown.map(({ s, off }, i) => {
          const tone = off ? '#dc2626' : '#059669'
          return (
            <button key={s.id} type="button" aria-disabled={off} aria-pressed={s.id === selected} aria-label={`${s.name}${off ? ', đang nghỉ' : ', đang hoạt động'}`}
              className={`${p.card} ${off ? p.off : ''} ${s.id === selected ? p.picked : ''} ${shake === s.id ? p.shake : ''}`}
              style={{ ['--tone' as string]: tone, ['--i' as string]: Math.min(i, 14) }} onClick={() => press({ s, off })}>
              <div className={p.top}>
                <span className={p.code}>{s.id}</span>
                <span className={p.status}><span className={`${p.dot} ${off ? '' : p.live}`} />{off ? 'Đang nghỉ' : 'Đang hoạt động'}</span>
              </div>
              <span className={p.avatar} style={{ ['--h' as string]: hue(s.id) }}>{initials(s.name)}</span>
              <div className={p.name}>{s.name}<small>{s.phone}</small></div>
              {off && <span className={p.lock}><i className="fa-solid fa-lock" />{s.offReason ?? 'Đang nghỉ'}{s.offTo ? ` đến ${formatDate(s.offTo)}` : ''}</span>}
              {s.id === selected && <span className={p.tick}><i className="fa-solid fa-circle-check" /> Đang chọn</span>}
            </button>
          )
        })}
        {!shown.length && <div className={p.empty}><i className="fa-solid fa-user" />Không có kết quả khớp.</div>}
      </div>
    </Modal>
  )
}
