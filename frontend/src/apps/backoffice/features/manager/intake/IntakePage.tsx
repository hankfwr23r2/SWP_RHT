import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { activeStaff } from '@shared/lib/booking'
import { appraisalDeadline } from '@shared/lib/deadlines'
import { COUNTRIES } from '@shared/config/network'
import { bookingsApi, invalidateBookingsListCache } from '@shared/services/bookings'
import { staffApi } from '@shared/services/staff'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, StaffRef } from '@shared/types/booking'
import type { StaffMember } from '@shared/types/staff'
import { useToast } from '@shared/ui/toast'
import { Modal } from '@shared/ui/Modal'
import { HorseDocsSection } from '../../../shared/BookingEvidence'
import { OrderInfo, PartiesInfo, ReviewChips } from '../../../shared/BookingParts'
import { SideTabsModal } from '../../../shared/SideTabsModal'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import { StaffPicker } from '../../../shared/StaffPicker'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'new' | 'running' | 'schedule'

const initials = (name: string) => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
const hue = (id: string) => [...id].reduce((n, c) => n + c.charCodeAt(0) * 7, 0) % 360

function matchesStaff(ref?: StaffRef, s?: StaffMember): boolean {
  if (!ref || !s) return false
  if (ref.id && ref.id === s.id) return true
  if (ref.name && (ref.name === s.name || ref.name === s.id)) return true
  return false
}

// Thẻ chọn nhân sự chuyên nghiệp cho Manager giao việc
function StaffAssignCard({
  roleTitle,
  roleDesc,
  icon,
  person,
  workload,
  onPick,
}: {
  roleTitle: string
  roleDesc: string
  icon: string
  person?: { id: string; name: string }
  workload?: number
  onPick: () => void
}) {
  const { t } = useTranslation()
  const isPicked = !!person
  return (
    <div
      onClick={onPick}
      role="button"
      tabIndex={0}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') onPick() }}
      style={{
        border: isPicked ? '1.5px solid #059669' : '1.5px dashed var(--line, #cbd5e1)',
        background: isPicked ? '#f0fdf4' : '#ffffff',
        borderRadius: 12,
        padding: '14px 16px',
        cursor: 'pointer',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        transition: 'all 0.15s ease',
        textAlign: 'left',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: '0.75rem', fontWeight: 750, textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--muted, #64748b)', display: 'flex', alignItems: 'center', gap: 6 }}>
          <i className={`fa-solid ${icon}`} style={{ color: isPicked ? '#059669' : '#ea580c' }} />
          {roleTitle}
        </span>
        {isPicked ? (
          <span style={{ fontSize: '0.72rem', fontWeight: 700, color: '#065f46', background: '#d1fae5', padding: '2px 8px', borderRadius: 999, display: 'inline-flex', alignItems: 'center', gap: 4 }}>
            <i className="fa-solid fa-circle-check" /> {t('intakePage.assigned', 'Đã chỉ định')}
          </span>
        ) : (
          <span style={{ fontSize: '0.72rem', fontWeight: 600, color: '#c2410c', background: '#ffedd5', padding: '2px 8px', borderRadius: 999 }}>
            {t('intakePage.unassigned', 'Chưa chọn')}
          </span>
        )}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div
          style={{
            width: 42,
            height: 42,
            borderRadius: 10,
            background: isPicked ? `hsl(${hue(person.id)}, 65%, 45%)` : '#f1f5f9',
            color: isPicked ? 'white' : '#94a3b8',
            display: 'grid',
            placeItems: 'center',
            fontWeight: 800,
            fontSize: '0.9rem',
            flexShrink: 0,
          }}
        >
          {isPicked ? initials(person.name) : <i className={`fa-solid ${icon}`} />}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
          <div style={{ fontSize: '0.95rem', fontWeight: 750, color: 'var(--navy, #0f172a)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {person ? person.name : t('intakePage.notChosen', 'Chưa chọn {{role}}', { role: roleTitle.toLowerCase() })}
          </div>
          <div style={{ fontSize: '0.76rem', color: 'var(--muted, #64748b)', marginTop: 1 }}>
            {roleDesc}
          </div>
          {isPicked && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.76rem', color: 'var(--navy, #0f172a)', marginTop: 4 }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, padding: '2px 8px', borderRadius: 6, background: '#f1f5f9', border: '1px solid #cbd5e1', fontWeight: 650 }}>
                <i className="fa-solid fa-briefcase" style={{ color: '#ea580c', fontSize: '0.7rem' }} />
                {t('intakePage.workload', 'Khối lượng công việc: {{count}} đơn', { count: workload ?? 0 })}
              </span>
            </div>
          )}
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--line, #f1f5f9)', paddingTop: 8, marginTop: 2 }}>
        <span style={{ fontSize: '0.75rem', color: 'var(--muted, #64748b)' }}>
          {isPicked ? t('intakePage.staffId', 'Mã nhân sự: {{id}}', { id: person.id }) : t('intakePage.clickToPick', 'Bấm để chọn từ danh sách')}
        </span>
        <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#ea580c', display: 'inline-flex', alignItems: 'center', gap: 4 }}>
          {isPicked ? t('intakePage.change', 'Thay đổi') : t('intakePage.pickPerson', 'Chọn người')} <i className="fa-solid fa-angle-right" style={{ fontSize: '0.72rem' }} />
        </span>
      </div>
    </div>
  )
}

export function IntakeModal({ b, allBookings, onClose, onDone }: { b: Booking; allBookings?: Booking[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { session } = useAuth()
  const { data: staff } = useLoad(staffApi.list)
  const specialists = staff ? activeStaff(staff, 'specialist') : []
  const coordinators = staff ? activeStaff(staff, 'coordinator') : []
  const [sp, setSp] = useState(b.intake?.specialist.id ?? '')
  const [co, setCo] = useState(b.intake?.coordinator.id ?? '')
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState<string | null>(null) // không null = đang nhập lý do từ chối

  useEffect(() => {
    if (!sp && (b.intake?.specialist.id || specialists[0]?.id)) {
      setSp(b.intake?.specialist.id || specialists[0].id)
    }
    if (!co && (b.intake?.coordinator.id || coordinators[0]?.id)) {
      setCo(b.intake?.coordinator.id || coordinators[0].id)
    }
  }, [specialists, coordinators, sp, co, b.intake])

  const spId = sp, coId = co

  // Tính số lượng đơn đang chạy mà từng nhân viên phụ trách
  const workloadMap = useMemo(() => {
    const map: Record<string, number> = {}
    if (!staff) return map
    const activeBookings = (allBookings ?? []).filter(
      x => !['completed', 'cancelled', 'rejected', 'quote_expired'].includes(x.status)
    )
    for (const s of staff) {
      const count = activeBookings.filter(item => {
        if (s.role === 'inspector') {
          return matchesStaff(item.intake?.specialist, s)
        }
        if (s.role === 'coordinator') {
          return matchesStaff(item.intake?.coordinator, s)
        }
        return false
      }).length
      map[s.id] = count
    }
    return map
  }, [staff, allBookings])

  const activate = async () => {
    const a = specialists.find(x => x.id === spId)
    const c = coordinators.find(x => x.id === coId)
    if (!a || !c) return
    setBusy(true)
    try {
      await bookingsApi.activate(b.id, session!.name, { id: a.id, name: a.name }, { id: c.id, name: c.name })
      toast(t('intakePage.receiveSuccess', 'Đã tiếp nhận {{id}}, đã giao Chuyên viên và Điều phối viên', { id: b.id }))
      onDone()
    } catch (e) { toast(e instanceof Error ? e.message : 'Không tiếp nhận được', 'error'); setBusy(false) }
  }

  const reject = async () => {
    setBusy(true)
    try {
      await bookingsApi.rejectOrder(b.id, session!.name, 'manager', reason ?? '')
      toast(t('intakePage.rejectSuccess', 'Đã từ chối {{id}}, khách nhận được lý do', { id: b.id }))
      onDone()
    } catch (e) { toast(e instanceof Error ? e.message : 'Không từ chối được', 'error'); setBusy(false) }
  }

  const [pick, setPick] = useState<'specialist' | 'coordinator' | null>(null)

  const waitHours = Math.max(0, Math.round((Date.now() - b.createdAt) / (1000 * 3600)))
  const slaDeadline = appraisalDeadline(b.createdAt, b.departAt)

  const overviewAndAssign = (
    <>
      {/* Thẻ hành trình tổng quan trực quan */}
      <div
        style={{
          border: '1px solid var(--line, #cbd5e1)',
          borderRadius: 12,
          background: 'linear-gradient(to right, #f8fafc, #ffffff)',
          padding: '14px 18px',
          marginBottom: 16,
          display: 'grid',
          gap: 10,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, borderBottom: '1px dashed var(--line, #cbd5e1)', paddingBottom: 10 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: '0.95rem', fontWeight: 800, color: 'var(--navy, #0f172a)' }}>
            <i className="fa-solid fa-map-location-dot" style={{ color: '#ea580c' }} />
            <span>{placeShort(b.origin.name)}</span>
            <i className="fa-solid fa-arrow-right-long" style={{ color: '#ea580c', fontSize: '0.85rem' }} />
            <span>{placeShort(b.dest.name)}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 700, padding: '2px 8px', borderRadius: 6, background: b.type === 'international' ? '#eff6ff' : '#f1f5f9', color: b.type === 'international' ? '#1d4ed8' : '#475569', border: '1px solid var(--line, #cbd5e1)' }}>
              {b.type === 'international' ? `Quốc tế (${COUNTRIES[b.origin.country]?.name || b.origin.country} → ${COUNTRIES[b.dest.country]?.name || b.dest.country})` : t('managerBoard.domestic', 'Trong nước')}
            </span>
            {waitHours >= 24 && (
              <span style={{ fontSize: '0.72rem', fontWeight: 700, padding: '2px 8px', borderRadius: 999, background: '#fef3c7', color: '#b45309', border: '1px solid #fde68a' }}>
                <i className="fa-regular fa-clock" /> {t('intakePage.waitedHours', 'Chờ {{hours}}h', { hours: waitHours })}
              </span>
            )}
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10, fontSize: '0.82rem', color: 'var(--muted, #64748b)' }}>
          <div>
            <b>{t('intakePage.departure', 'Khởi hành')}:</b> <span style={{ color: 'var(--navy, #0f172a)' }}>{formatDate(b.departAt)}</span>
          </div>
          <div>
            <b>{t('intakePage.slaDeadline', 'Hạn thẩm định (SLA)')}:</b> <span style={{ color: '#059669', fontWeight: 700 }}>{formatDate(slaDeadline)} (17:00)</span>
          </div>
          <div>
            <b>{t('intakePage.horsesCount', 'Số lượng ngựa')}:</b> <span style={{ color: 'var(--navy, #0f172a)', fontWeight: 700 }}>{t('intakePage.horsesUnit', '{{count}} con', { count: b.horses.length })}</span> ({b.horses.map(h => h.name).join(', ')})
          </div>
          {b.gate && (
            <div>
              <b>{t('intakePage.borderGate', 'Cửa khẩu')}:</b> <span style={{ color: 'var(--navy, #0f172a)' }}>{b.gate}</span>
            </div>
          )}
          <div>
            <b>{t('intakePage.bookedBy', 'Khách đặt đơn')}:</b> <span style={{ color: 'var(--navy, #0f172a)' }}>{b.customer}</span>
          </div>
          <div>
            <b>{t('intakePage.submittedAt', 'Gửi lúc')}:</b> <span style={{ color: 'var(--navy, #0f172a)' }}>{formatDateTime(b.createdAt)}</span>
          </div>
        </div>
      </div>

      <h4 style={{ margin: '0 0 10px', fontSize: '0.98rem', fontWeight: 750, color: 'var(--navy, #0f172a)' }}>
        {t('intakePage.parallelAssignTitle', 'Giao việc thẩm định song song')}
      </h4>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 14 }}>
        <StaffAssignCard
          roleTitle={t('intakePage.specialistRole', 'Chuyên viên')}
          roleDesc={t('intakePage.specialistDesc', 'Duyệt hồ sơ thú y, hộ chiếu & xét nghiệm ngựa')}
          icon="fa-user-doctor"
          person={spId ? specialists.find(x => x.id === spId) : undefined}
          workload={spId ? (workloadMap[spId] ?? 0) : undefined}
          onPick={() => setPick('specialist')}
        />
        <StaffAssignCard
          roleTitle={t('intakePage.coordinatorRole', 'Điều phối viên')}
          roleDesc={t('intakePage.coordinatorDesc', 'Bố trí xe, tài xế, hộ tống & lập lộ trình di chuyển')}
          icon="fa-route"
          person={coId ? coordinators.find(x => x.id === coId) : undefined}
          workload={coId ? (workloadMap[coId] ?? 0) : undefined}
          onPick={() => setPick('coordinator')}
        />
      </div>
    </>
  )

  return (
    <>
      <SideTabsModal
        onClose={onClose} title={`Tiếp nhận ${b.id}`} subtitle={`${b.customer} · gửi lúc ${formatDateTime(b.createdAt)}`}
        top={reason !== null && (
          <div className="form-group">
            <label htmlFor="rj" className="required">{t('intakePage.rejectReason', 'Lý do từ chối')} <span style={{ color: 'var(--text-muted, #64748b)', fontWeight: 400, fontSize: '0.85em' }}>{t('intakePage.clientWillSee', '(khách sẽ thấy)')}</span></label>
            <textarea id="rj" className="form-control" rows={3} value={reason} onChange={e => setReason(e.target.value)} />
          </div>
        )}
        tabs={[
          { id: 'assign', label: t('intakePage.tabAssign', 'Tổng quan & Giao việc'), icon: 'fa-user-check', state: spId && coId ? 'ok' : 'warn', content: overviewAndAssign },
          { id: 'docs', label: `${t('intakePage.tabHorseDocs', 'Hồ sơ ngựa ({{count}})', { count: b.horses.length })}`, icon: 'fa-file-medical', content: <HorseDocsSection b={b} reviewed={false} /> },
          { id: 'order', label: t('intakePage.tabTripInfo', 'Thông tin chuyến đi'), icon: 'fa-clipboard-list', content: <OrderInfo b={b} /> },
          { id: 'customer', label: t('intakePage.tabParties', 'Bên gửi & Bên nhận'), icon: 'fa-users', content: <PartiesInfo b={b} /> },
        ]}
        footer={reason === null
          ? <><button className="btn btn-ghost" onClick={onClose}>{t('intakePage.closeBtn', 'Đóng')}</button><button className="btn btn-ghost" onClick={() => setReason('')}><i className="fa-solid fa-ban" /> {t('intakePage.rejectOrderBtn', 'Từ chối đơn')}</button><button className="btn btn-primary" disabled={busy || !spId || !coId} onClick={activate}><i className="fa-solid fa-play" /> {t('intakePage.activateIntakeBtn', 'Tiếp nhận và kích hoạt thẩm định')}</button></>
          : <><button className="btn btn-ghost" onClick={() => setReason(null)}>{t('intakePage.backBtn', 'Quay lại')}</button><button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={reject}><i className="fa-solid fa-ban" /> {t('intakePage.confirmRejectBtn', 'Xác nhận từ chối')}</button></>}
      />
      {pick && staff && (
        <StaffPicker
          task={pick}
          staff={staff}
          workloadMap={workloadMap}
          selected={(pick === 'specialist' ? spId : coId) ?? ''}
          onPick={id => (pick === 'specialist' ? setSp(id) : setCo(id))}
          onClose={() => setPick(null)}
        />
      )}
    </>
  )
}

export default function IntakePage() {
  const { t } = useTranslation()
  const { all, reload } = usePageData<typeof bookingListLoader>()
  const [params, setParams] = useSearchParams()
  const group = params.get('group')
  const tab: Tab = group === 'schedule' || group === 'running' ? group : 'new'
  const setTab = (next: Tab) => setParams(next === 'new' ? {} : { group: next })
  const [open, setOpen] = useState<Booking | null>(null)
  const list = all ?? []
  const fresh = list.filter(b => b.status === 'pending_intake')
  const schedule = list.filter(b => b.status === 'schedule_change_required')
  const running = list.filter(b => b.status === 'under_review')
  const columns: Column<Booking>[] = [
    { head: t('intakePage.colOrderId', 'Mã đơn'), cell: b => idCell(b) },
    { head: t('intakePage.colCustomer', 'Khách hàng'), cell: b => b.customer, nowrap: true },
    {
      head: t('intakePage.colRoute', 'Tuyến'),
      cell: b => (
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          {routeCell(placeShort(b.origin.name), placeShort(b.dest.name))}
          {b.type === 'international' && (
            <span
              style={{
                fontSize: '0.68rem',
                fontWeight: 750,
                padding: '1px 6px',
                borderRadius: 4,
                background: '#eff6ff',
                color: '#1d4ed8',
                border: '1px solid #bfdbfe',
                textTransform: 'uppercase',
              }}
              title="Chuyến vận chuyển quốc tế"
            >
              {t('managerBoard.international', 'Quốc tế')}
            </span>
          )}
        </div>
      ),
    },
    { head: t('intakePage.colDeparture', 'Khởi hành'), cell: b => formatDate(b.departAt), nowrap: true },
    { head: t('intakePage.colHorses', 'Ngựa'), cell: b => t('intakePage.horsesUnit', '{{count}} con', { count: b.horses.length }), nowrap: true },
    tab === 'new'
      ? {
          head: t('intakePage.colSubmitted', 'Gửi lúc'),
          cell: b => {
            const waitHours = Math.max(0, Math.round((Date.now() - b.createdAt) / (1000 * 3600)))
            const isWaitingLong = waitHours >= 24
            return (
              <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <span>{formatDateTime(b.createdAt)}</span>
                {isWaitingLong && (
                  <span
                    style={{
                      fontSize: '0.72rem',
                      fontWeight: 700,
                      padding: '2px 7px',
                      borderRadius: 999,
                      background: '#fef3c7',
                      color: '#b45309',
                      border: '1px solid #fde68a',
                      whiteSpace: 'nowrap',
                    }}
                    title={`Đơn đã chờ tiếp nhận ${waitHours} giờ`}
                  >
                    <i className="fa-regular fa-clock" /> {t('intakePage.waitedHours', 'Chờ {{hours}}h', { hours: waitHours })}
                  </span>
                )}
              </div>
            )
          },
          nowrap: true,
        }
      : {
          head: t('intakePage.colAppraisal', 'Thẩm định & Hạn SLA'),
          cell: b => {
            const deadline = appraisalDeadline(b.createdAt, b.departAt)
            const msLeft = deadline - Date.now()
            const daysLeft = Math.ceil(msLeft / (1000 * 3600 * 24))
            const isOverdue = msLeft < 0
            return (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                <ReviewChips b={b} />
                <span
                  style={{
                    fontSize: '0.72rem',
                    color: isOverdue ? '#dc2626' : '#475569',
                    fontWeight: isOverdue ? 700 : 500,
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                  }}
                  title={`Hạn chót thẩm định toàn bộ đơn: ${formatDateTime(deadline)}`}
                >
                  <i className={`fa-regular ${isOverdue ? 'fa-circle-exclamation' : 'fa-hourglass-half'}`} style={{ color: isOverdue ? '#dc2626' : '#ea580c' }} />
                  {isOverdue ? t('intakePage.slaOverdue', 'Quá hạn SLA ({{date}})', { date: formatDate(deadline) }) : t('intakePage.slaDueIn', 'Hạn SLA: {{date}} ({{days}} ngày)', { date: formatDate(deadline), days: daysLeft })}
                </span>
              </div>
            )
          },
        },
    { head: t('intakePage.colStatus', 'Trạng thái'), cell: b => statusCell(b.status) },
    { head: t('intakePage.colAction', 'Thao tác'), cell: b => (tab !== 'running' ? <button className="btn btn-primary btn-sm" onClick={() => setOpen(b)}>{tab === 'schedule' ? 'Xem yêu cầu' : t('intakePage.intakeAction', 'Tiếp nhận')}</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title={t('intakePage.title', 'Tiếp nhận đơn hàng')} subtitle={t('intakePage.subtitle', 'Giao Chuyên viên và Điều phối viên thẩm định song song.')} tabs={[['new', t('intakePage.tabNew', 'Chờ tiếp nhận'), fresh.length], ['running', t('intakePage.tabRunning', 'Đang thẩm định'), running.length], ['schedule', 'Cần xác nhận lại lịch', schedule.length]]} tab={tab} onTab={setTab} hot={['new', 'schedule']}
        rows={tab === 'schedule' ? schedule : tab === 'new' ? fresh : running} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all}
        emptyText={tab === 'schedule' ? 'Không có đơn nào cần xác nhận lại lịch.' : tab === 'new' ? t('intakePage.emptyNew', 'Không có đơn nào chờ tiếp nhận.') : t('intakePage.emptyRunning', 'Không có đơn nào đang thẩm định.')} />
      {open?.status === 'schedule_change_required' && <Modal title={`${open.id} · Cần xác nhận lại lịch`} onClose={() => setOpen(null)}>
        <p>Ngày khởi hành cũ: <b>{formatDate(open.departAt)}</b>. Chờ khách chọn ngày mới, hủy yêu cầu hoặc nhờ hỗ trợ.</p>
        <p>Người gửi: {open.consignor.name} · <a href={`tel:${open.consignor.phone}`}>{open.consignor.phone}</a></p>
        {open.scheduleChange?.supportRequestedAt && <p className="alert alert-warning">Khách đã yêu cầu liên hệ hỗ trợ lúc {formatDateTime(open.scheduleChange.supportRequestedAt)}.</p>}
        <p className="form-hint">Sau khi thống nhất, khách chọn ngày mới trong trang đơn hàng. Đơn sẽ quay về chờ tiếp nhận để kiểm tra lại hồ sơ, xe và kíp xe.</p>
      </Modal>}
      {open && open.status !== 'schedule_change_required' && (
        <IntakeModal
          b={open}
          allBookings={list}
          onClose={() => setOpen(null)}
          onDone={() => { invalidateBookingsListCache(); setOpen(null); reload() }}
        />
      )}
    </>
  )
}

