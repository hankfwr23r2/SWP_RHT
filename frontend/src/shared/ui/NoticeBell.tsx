// Chuông thông báo trên thanh menu: số chưa đọc, danh sách đẩy xuống, bấm một tin để mở đơn liên quan.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { formatDateTime } from '../lib/format'
import { noticesApi, type Notice } from '../services/notices'
import { useToast } from './toast'
import s from './NoticeBell.module.css'

export function NoticeBell() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Notice[]>([])
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deletingAll, setDeletingAll] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const listRequest = useRef(0)
  const role = session?.role
  const name = session?.name

  const load = useCallback(() => {
    if (!role) return
    const request = ++listRequest.current
    noticesApi.list(role).then(nextItems => {
      if (request === listRequest.current) setItems(nextItems)
    })
  }, [role])
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
  const deleteNotice = async (n: Notice) => {
    if (!role) return
    setDeletingId(n.id)
    try {
      await noticesApi.delete(n.id, role)
      listRequest.current += 1
      setItems(current => current.filter(item => item.id !== n.id))
      toast('Đã xóa thông báo.', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể xóa thông báo.', 'error')
    } finally {
      setDeletingId(null)
    }
  }
  const deleteAll = async () => {
    if (!role || !items.length || !window.confirm('Bạn có chắc muốn xóa tất cả thông báo?')) return
    setDeletingAll(true)
    try {
      await noticesApi.deleteAll(role)
      listRequest.current += 1
      setItems([])
      toast('Đã xóa tất cả thông báo.', 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : 'Không thể xóa tất cả thông báo.', 'error')
    } finally {
      setDeletingAll(false)
    }
  }

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
            <div className={s.actions}>
              <button className={s.readAll} onClick={readAll} disabled={!unread || deletingAll}>Đọc tất cả</button>
              <button className={s.deleteAll} onClick={deleteAll} disabled={!items.length || deletingAll || deletingId !== null}>
                {deletingAll ? 'Đang xóa...' : 'Xóa tất cả'}
              </button>
            </div>
          </div>
          {!items.length && <p className={s.empty}><i className="fa-regular fa-bell-slash" aria-hidden="true" /> Chưa có thông báo nào.</p>}
          <ul className={s.list}>
            {items.map(n => (
              <li key={n.id}>
                <div className={s.itemRow}>
                  <button className={`${s.item} ${n.read ? '' : s.unread}`} onClick={() => openNotice(n)}>
                    {!n.read && <span className={s.dot} aria-hidden="true" />}
                    <span className={s.title}>{n.title}</span>
                    <span className={s.text}>{n.text}</span>
                    <span className={s.time}>{formatDateTime(n.at)}</span>
                  </button>
                  <button
                    className={s.delete}
                    type="button"
                    aria-label={`Xóa thông báo: ${n.title}`}
                    title="Xóa thông báo"
                    disabled={deletingAll || deletingId !== null}
                    onClick={() => deleteNotice(n)}
                  >
                    <i className="fa-solid fa-trash" aria-hidden="true" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
