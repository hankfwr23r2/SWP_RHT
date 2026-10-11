// Chuông thông báo trên thanh menu: số chưa đọc, danh sách đẩy xuống, bấm một tin để mở đơn liên quan.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../lib/format'
import { noticesApi, type Notice } from '../services/notices'
import s from './NoticeBell.module.css'

export function NoticeBell() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Notice[]>([])
  const box = useRef<HTMLDivElement>(null)
  const role = session?.role
  const name = session?.name

  const load = useCallback(() => { if (role) noticesApi.list(role, name).then(setItems) }, [role, name])
  // Cập nhật định kỳ để thấy tin mới do vai trò khác vừa gửi (cùng tab)
  useEffect(() => {
    load()
    const t = setInterval(load, 3000)
    return () => clearInterval(t)
  }, [load])
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => { if (!box.current?.contains(e.target as Node)) setOpen(false) }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDown); document.removeEventListener('keydown', onKey) }
  }, [open])

  const unread = items.filter(n => !n.read).length
  const openNotice = async (n: Notice) => {
    await noticesApi.markRead(n.id)
    setOpen(false)
    load()
    if (n.link) navigate(n.link)
  }
  const readAll = async () => { if (role) { await noticesApi.markAllRead(role, name); load() } }

  return (
    <div className={s.wrap} ref={box}>
      <button className={s.bell} aria-label={unread ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'} aria-expanded={open} onClick={() => { setOpen(o => !o); load() }}>
        <i className="fa-solid fa-bell" aria-hidden="true" />
        {unread > 0 && <span className={s.badge}>{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className={s.panel} role="dialog" aria-label="Thông báo">
          <div className={s.head}>
            <b>Thông báo</b>
            <button className={s.readAll} onClick={readAll} disabled={!unread}>Đọc tất cả</button>
          </div>
          {!items.length && <p className={s.empty}><i className="fa-regular fa-bell-slash" aria-hidden="true" /> Chưa có thông báo nào.</p>}
          <ul className={s.list}>
            {items.map(n => (
              <li key={n.id}>
                <button className={`${s.item} ${n.read ? '' : s.unread}`} onClick={() => openNotice(n)}>
                  {!n.read && <span className={s.dot} aria-hidden="true" />}
                  <span className={s.title}>{n.title}</span>
                  <span className={s.text}>{n.text}</span>
                  <span className={s.time}>{formatDateTime(n.at)}</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
