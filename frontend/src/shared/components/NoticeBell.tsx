// Chuông thông báo trên thanh menu: số chưa đọc, danh sách đẩy xuống, bấm một tin để mở trang liên quan.
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { notificationsApi, type Notice } from '@shared/api/notifications'
import { useAutoRefresh } from '@shared/hooks/useLoad'
import { formatDateTime } from '@shared/lib/format'
import { ConfirmDialog } from '@shared/ui/ConfirmDialog'
import { useToast } from '@shared/ui/toast'
import s from './NoticeBell.module.css'

export function NoticeBell() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const toast = useToast()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Notice[]>([])
  const [loadError, setLoadError] = useState(false)
  const [busy, setBusy] = useState(false)
  const [confirmAll, setConfirmAll] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  const listRequest = useRef(0)

  const load = useCallback(() => {
    const request = ++listRequest.current
    notificationsApi.mine().then(next => {
      if (request === listRequest.current) { setItems(next); setLoadError(false) }
    }).catch(() => { if (request === listRequest.current) setLoadError(true) })
  }, [])
  useEffect(() => { load() }, [load])
  useAutoRefresh(load)
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
    if (!n.read) await notificationsApi.markRead(n.id!).catch(() => undefined)
    setOpen(false)
    load()
    if (n.linkUrl) navigate(n.linkUrl)
  }
  const readAll = async () => { await notificationsApi.markAllRead().catch(() => undefined); load() }
  const remove = async (action: () => Promise<void>, done: string) => {
    setBusy(true)
    try {
      await action()
      toast(done, 'success')
      load()
    } catch {
      toast(t('notices.deleteFailed'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={s.wrap} ref={box}>
      <button className={s.bell} aria-label={unread ? t('notices.bellUnread', { count: unread }) : t('notices.bell')} aria-expanded={open}
        onClick={() => { setOpen(o => !o); load() }}>
        <i className="fa-solid fa-bell" aria-hidden="true" />
        {unread > 0 && <span className={s.badge}>{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <div className={s.panel} role="dialog" aria-label={t('notices.bell')}>
          <div className={s.head}>
            <b>{t('notices.bell')}</b>
            <div className={s.actions}>
              <button className={s.readAll} onClick={readAll} disabled={!unread || busy}>{t('notices.readAll')}</button>
              <button className={s.deleteAll} onClick={() => setConfirmAll(true)} disabled={!items.length || busy}>
                {busy ? t('notices.deleting') : t('notices.deleteAll')}
              </button>
            </div>
          </div>
          {!items.length && loadError
            ? <p className={s.empty} role="alert">{t('notices.loadFailed')} <button className={s.readAll} onClick={load}>{t('notices.retry')}</button></p>
            : !items.length && <p className={s.empty}><i className="fa-regular fa-bell-slash" aria-hidden="true" /> {t('notices.empty')}</p>}
          <ul className={s.list}>
            {items.map(n => (
              <li key={n.id}>
                <div className={s.itemRow}>
                  <button className={`${s.item} ${n.read ? '' : s.unread}`} onClick={() => openNotice(n)}>
                    {!n.read && <span className={s.dot} aria-hidden="true" />}
                    <span className={s.title}>{n.title}</span>
                    <span className={s.text}>{n.message}</span>
                    <span className={s.time}>{n.createdAt ? formatDateTime(new Date(n.createdAt)) : ''}</span>
                  </button>
                  <button className={s.delete} type="button" aria-label={t('notices.deleteOne', { title: n.title })} title={t('notices.deleteOne', { title: n.title })}
                    disabled={busy} onClick={() => remove(() => notificationsApi.remove(n.id!), t('notices.deleted'))}>
                    <i className="fa-solid fa-trash" aria-hidden="true" />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
      {confirmAll && (
        <ConfirmDialog title={t('notices.confirmDeleteAllTitle')} message={t('notices.confirmDeleteAllMessage')} confirmLabel={t('notices.deleteAll')} tone="danger"
          onCancel={() => setConfirmAll(false)}
          onConfirm={() => { setConfirmAll(false); return remove(notificationsApi.removeAll, t('notices.deletedAll')) }} />
      )}
    </div>
  )
}
