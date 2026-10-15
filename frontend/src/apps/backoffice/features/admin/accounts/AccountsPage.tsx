// Quản trị viên: quản lý tài khoản hệ thống. Danh sách theo vai trò, tạo tài khoản nhân viên, sửa thông tin,
// khóa / mở khóa, đặt lại mật khẩu, xem lịch sử thao tác của từng tài khoản.
import { useState } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDateTime } from '@shared/lib/format'
import { accountsApi, isOpsRole } from '@shared/services/accounts'
import type { Account } from '@shared/services/mock/accounts'
import { useLoad } from '@shared/services/useLoad'
import { ROLE_LABEL, type Role } from '@shared/types/role'
import { FormSelect } from '@shared/ui/FormSelect'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { ListPage, type Column, type TabDef } from '../../../shared/ListPage'
import s from './Accounts.module.css'

type Tab = 'all' | Role | 'locked'
const ROLES: Role[] = ['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort', 'customer']
// Vai trò có thể chọn khi tạo tài khoản mới (khách tự đăng ký nên không tạo ở đây)
const CREATABLE: Role[] = ['admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort']
const initials = (name: string) => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()

type Dialog =
  | { kind: 'edit'; account: Account | null } // null = tạo mới
  | { kind: 'lock'; account: Account }
  | { kind: 'reset'; account: Account }
  | { kind: 'secret'; title: string; who: string; email: string; password: string }

function AccountModal({ account, actor, defaultRole, onClose, onDone }: { account: Account | null; actor: string; defaultRole: Role; onClose: () => void; onDone: (result?: { account: Account; password: string }) => void }) {
  const toast = useToast()
  const [name, setName] = useState(account?.name ?? '')
  const [email, setEmail] = useState(account?.email ?? '')
  const [phone, setPhone] = useState(account?.phone ?? '')
  const [role, setRole] = useState<Role>(account?.role ?? defaultRole)
  const [busy, setBusy] = useState(false)
  const nameLocked = !!account && isOpsRole(account.role)
  const save = async () => {
    setBusy(true)
    try {
      if (account) { await accountsApi.update(account.id, { name, email, phone }, actor); toast('Đã lưu thay đổi'); onDone() } else { onDone(await accountsApi.create({ name, email, phone, role }, actor)) }
    } catch (e) { toast(e instanceof Error ? e.message : 'Không lưu được', 'error'); setBusy(false) }
  }
  return (
    <Modal wide onClose={onClose} title={account ? `Tài khoản ${account.id}` : 'Tạo tài khoản'} subtitle={account ? ROLE_LABEL[account.role] : 'Mật khẩu tạm được tạo tự động và chỉ hiện một lần sau khi tạo.'}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" disabled={busy || !name.trim() || !email.trim()} onClick={save}><i className="fa-solid fa-floppy-disk" /> {account ? 'Lưu thay đổi' : 'Tạo tài khoản'}</button></>}>
      <div className={s.form}>
        <div className="form-group"><label htmlFor="ac-name" className="required">Họ tên</label><input id="ac-name" className="form-control" value={name} disabled={nameLocked} onChange={e => setName(e.target.value)} />{nameLocked && <div className="form-hint">Họ tên gắn với đơn và lịch phân công nên không đổi được.</div>}</div>
        <div className="form-group"><label htmlFor="ac-mail" className="required">Email đăng nhập</label><input id="ac-mail" type="email" className="form-control" value={email} onChange={e => setEmail(e.target.value)} /></div>
        <div className="form-group"><label htmlFor="ac-phone">Số điện thoại</label><input id="ac-phone" className="form-control" value={phone} onChange={e => setPhone(e.target.value)} /></div>
        <div className="form-group">
          <label htmlFor="ac-role">Vai trò</label>
          {account ? <input id="ac-role" className="form-control" value={ROLE_LABEL[account.role]} disabled /> : (
            <FormSelect id="ac-role" className="form-control" value={role} onChange={e => setRole(e.target.value as Role)}>{CREATABLE.map(r => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</FormSelect>
          )}
          <div className="form-hint">{account ? 'Vai trò không đổi sau khi tạo. Cần vai trò khác thì tạo tài khoản mới.' : 'Nhân viên mới tự vào danh bạ để được phân công.'}</div>
        </div>
      </div>
      {account && (
        <>
          <h4 style={{ margin: '18px 0 10px' }}>Lịch sử thao tác</h4>
          <ol className={s.history}>{[...account.history].sort((a, z) => z.time - a.time).map(h => <li key={h.time + h.text}><span>{h.text}</span><small>{h.actor} · {formatDateTime(h.time)}</small></li>)}</ol>
        </>
      )}
    </Modal>
  )
}

export default function AccountsPage() {
  const toast = useToast()
  const { session } = useAuth()
  const me = { name: session!.name, email: session!.username }
  const { data: all, reload } = useLoad(accountsApi.list)
  // Danh mục tài khoản nằm ở thanh bên: loại đang xem lấy từ đường dẫn /admin/accounts/:role
  const { role: param } = useParams()
  const tab: Tab = [...ROLES, 'locked'].includes(param as Tab) ? (param as Tab) : 'all'
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [why, setWhy] = useState('')
  const [busy, setBusy] = useState(false)
  const list = all ?? []
  const count = (t: Tab) => (t === 'all' ? list.length : t === 'locked' ? list.filter(a => a.status === 'locked').length : list.filter(a => a.role === t).length)
  const tabs: TabDef<Tab>[] = [['all', 'Tất cả', list.length], ...ROLES.map((r): TabDef<Tab> => [r, ROLE_LABEL[r], count(r)]), ['locked', 'Đã khóa', count('locked')]]
  const rows = list.filter(a => (tab === 'all' ? true : tab === 'locked' ? a.status === 'locked' : a.role === tab))
  const close = () => { setDialog(null); setWhy(''); setBusy(false) }
  const run = async (fn: () => Promise<void>) => { setBusy(true); try { await fn() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error'); setBusy(false) } }

  const columns: Column<Account>[] = [
    { head: 'Tài khoản', minWidth: 220, cell: a => <div className={s.who}><span className={s.avatar}>{initials(a.name)}</span><div><b>{a.name}</b><small>{a.email}</small></div></div> },
    { head: 'Số điện thoại', cell: a => a.phone || '-', nowrap: true },
    { head: 'Vai trò', cell: a => <span className={`${s.role} ${s[`r_${a.role}`]}`}>{ROLE_LABEL[a.role]}</span> },
    { head: 'Trạng thái', cell: a => <span className={`badge ${a.status === 'active' ? 'badge-success' : 'badge-danger'}`}><i className={`fa-solid ${a.status === 'active' ? 'fa-circle-check' : 'fa-lock'}`} /> {a.status === 'active' ? 'Hoạt động' : 'Đã khóa'}</span> },
    { head: 'Đăng nhập gần nhất', cell: a => (a.lastLoginAt ? formatDateTime(a.lastLoginAt) : 'Chưa đăng nhập'), nowrap: true },
    { head: 'Thao tác', right: true, cell: a => (
      <span className={s.actions}>
        <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'edit', account: a })}>Sửa</button>
        <button className="btn btn-ghost btn-sm" onClick={() => setDialog({ kind: 'reset', account: a })} title="Đặt lại mật khẩu"><i className="fa-solid fa-key" /></button>
        <button className={`btn btn-ghost btn-sm ${a.status === 'active' ? 'text-red' : ''}`} onClick={() => (a.status === 'active' ? setDialog({ kind: 'lock', account: a }) : run(async () => { await accountsApi.setLocked(a.id, false, me); toast(`Đã mở khóa ${a.name}`); close(); reload() }))}>{a.status === 'active' ? 'Khóa' : 'Mở khóa'}</button>
      </span>
    ) },
  ]

  return (
    <>
      <ListPage title={tab === 'all' ? 'Tài khoản hệ thống' : tab === 'locked' ? 'Tài khoản đã khóa' : `Tài khoản · ${ROLE_LABEL[tab]}`} subtitle="Tạo tài khoản nhân viên, khóa hoặc mở khóa, đặt lại mật khẩu. Khách hàng tự đăng ký; bạn chỉ khóa hoặc mở khóa được." tabs={tabs} tab={tab} onTab={() => undefined} hideTabs
        rows={rows} rowKey={a => a.id} columns={columns} haystack={a => [a.id, a.name, a.email, a.phone, ROLE_LABEL[a.role]]} loaded={!!all} emptyText="Không có tài khoản nào ở mục này." searchPlaceholder="Tìm theo tên, email, số điện thoại…" hotRow={a => a.status === 'locked'}
        actions={<button className="btn btn-primary" onClick={() => setDialog({ kind: 'edit', account: null })}><i className="fa-solid fa-user-plus" /> Tạo tài khoản</button>} />

      {dialog?.kind === 'edit' && (
        <AccountModal account={dialog.account} actor={me.name} defaultRole={CREATABLE.includes(tab as Role) ? (tab as Role) : 'specialist'} onClose={close} onDone={result => {
          close(); reload()
          if (result) setDialog({ kind: 'secret', title: 'Đã tạo tài khoản', who: result.account.name, email: result.account.email, password: result.password })
        }} />
      )}

      {dialog?.kind === 'lock' && (
        <Modal onClose={close} title={`Khóa tài khoản ${dialog.account.name}?`} subtitle="Người này sẽ không đăng nhập được cho đến khi bạn mở khóa. Nhân viên đang đăng nhập vẫn dùng được đến khi đăng xuất."
          footer={<><button className="btn btn-ghost" onClick={close}>Hủy</button><button className="btn btn-danger" disabled={busy} onClick={() => run(async () => { await accountsApi.setLocked(dialog.account.id, true, me, why); toast(`Đã khóa ${dialog.account.name}`); close(); reload() })}><i className="fa-solid fa-lock" /> Khóa tài khoản</button></>}>
          <div className="form-group" style={{ margin: 0 }}><label htmlFor="ac-why">Lý do (không bắt buộc)</label><textarea id="ac-why" className="form-control" rows={3} value={why} onChange={e => setWhy(e.target.value)} /></div>
        </Modal>
      )}

      {dialog?.kind === 'reset' && (
        <Modal onClose={close} title={`Đặt lại mật khẩu cho ${dialog.account.name}?`} subtitle="Mật khẩu cũ mất hiệu lực ngay. Mật khẩu tạm mới chỉ hiện một lần, hãy gửi cho người dùng."
          footer={<><button className="btn btn-ghost" onClick={close}>Hủy</button><button className="btn btn-primary" disabled={busy} onClick={() => run(async () => { const password = await accountsApi.resetPassword(dialog.account.id, me.name); close(); reload(); setDialog({ kind: 'secret', title: 'Đã đặt lại mật khẩu', who: dialog.account.name, email: dialog.account.email, password }) })}><i className="fa-solid fa-key" /> Đặt lại mật khẩu</button></>}>
          <p>Tài khoản: <b>{dialog.account.email}</b></p>
        </Modal>
      )}

      {dialog?.kind === 'secret' && (
        <Modal onClose={close} title={dialog.title} subtitle={`${dialog.who} · ${dialog.email}`} footer={<button className="btn btn-primary" onClick={close}>Đã ghi lại, đóng</button>}>
          <div className={s.secret}><span>Mật khẩu tạm</span><code>{dialog.password}</code><button className="btn btn-outline btn-sm" onClick={() => { navigator.clipboard?.writeText(dialog.password); toast('Đã sao chép mật khẩu', 'info') }}><i className="fa-regular fa-copy" /> Sao chép</button></div>
          <p className="form-hint" style={{ marginTop: 10 }}>Mật khẩu này chỉ hiện một lần. Bản thử nghiệm lưu trong phiên trình duyệt; khi có backend, mật khẩu được băm ở server.</p>
        </Modal>
      )}
    </>
  )
}
