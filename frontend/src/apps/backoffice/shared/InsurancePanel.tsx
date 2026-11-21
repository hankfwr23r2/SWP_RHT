import { useEffect, useRef, useState } from 'react'
import { Link, useBlocker } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Booking } from '@shared/types/booking'
import { horseInsuranceTask, insuranceAfterDeposit, insuranceBeforeQuote, insuranceDepartureProblem, insuranceReviewProblem, insuranceRevisionPending, type HorseInsurance } from '@shared/types/insurance'
import { InsuranceInputs, InsuranceSummary } from '@shared/ui/InsuranceFields'
import { specialistApi } from '@shared/api/specialist'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { invalidateBookingsListCache } from '@shared/services/bookings'
import { useToast } from '@shared/ui/toast'
import s from './InsurancePanel.module.css'

export function InsuranceLink({ b }: { b: Booking }) {
  const { t } = useTranslation()
  const remaining = b.horses.filter(h => insuranceBeforeQuote(b.status)
    ? !h.insurance.option || (h.insurance.option !== 'none' && !h.insurance.reviewed)
    : h.insurance.option === 'assistance' && !h.insurance.arranged).length
  // Bảo hiểm làm ở trang riêng: còn việc thì nói rõ việc đó chặn bước nào (báo giá hoặc xe xuất phát)
  const blocks = insuranceBeforeQuote(b.status)
    ? t('specialistInsurance.blocksQuote', 'Quản lý chưa gửi được báo giá cho tới khi bảo hiểm xong.')
    : t('specialistInsurance.blocksDeparture', 'Tài xế chưa bắt đầu hành trình được cho tới khi hợp đồng bảo hiểm được xác nhận.')
  return (
    <div className={`card ${s.link}`} role={remaining ? 'alert' : undefined} style={remaining ? { borderColor: 'var(--amber, #d97706)', background: 'var(--warning-soft, #fffbeb)' } : undefined}>
      <div>
        <b>{remaining ? <><i className="fa-solid fa-triangle-exclamation" /> </> : null}{t('specialistInsurance.title', 'Bảo hiểm')}</b>
        <p>{remaining ? <>{t('specialistInsurance.linkCardRemaining', '{{remaining}}/{{total}} ngựa còn cần xử lý bảo hiểm.', { remaining, total: b.horses.length })} {blocks}</> : t('specialistInsurance.linkCardDone', 'Xem tiến độ báo giá và hợp đồng bảo hiểm.')}</p>
      </div>
      <Link className="btn btn-outline btn-sm" to={`/specialist/insurance/${b.id}`}>
        {t('specialistInsurance.openInsurance', 'Mở bảo hiểm')} <i className="fa-solid fa-arrow-right" />
      </Link>
    </div>
  )
}

// by: tên chuyên viên ghi vào lịch sử khi yêu cầu khách bổ sung; bỏ trống thì backend dùng tài khoản đăng nhập
export function InsurancePanel({ b, onDone, by }: { b: Booking; onDone: () => void; by?: string }) {
  const { t } = useTranslation()
  const tasks = new Map(b.horses.map(h => [h.horseId, horseInsuranceTask(b.status, h.insurance)]))
  const remaining = b.horses.filter(h => tasks.get(h.horseId)?.pending).length
  // Mở sẵn ngựa đầu tiên còn việc để chuyên viên không phải dò từng con
  const [picked, setPicked] = useState(() => b.horses.find(h => tasks.get(h.horseId)?.pending)?.horseId ?? b.horses[0]?.horseId ?? '')
  const [locked, setLocked] = useState(false)
  const horse = b.horses.find(h => h.horseId === picked) ?? b.horses[0]
  return (
    <section className="card">
      <div className="card-header">
        <h3>{insuranceBeforeQuote(b.status) ? t('specialistInsurance.quoteAndCheck', 'Báo giá và kiểm tra bảo hiểm') : t('specialistInsurance.insuranceByBooking', 'Bảo hiểm theo đơn')}</h3>
        <span className={`badge ${remaining ? 'badge-warning' : 'badge-success'}`}>
          {remaining ? t('specialistInsurance.horsesRemaining', '{{remaining}}/{{total}} ngựa còn cần xử lý', { remaining, total: b.horses.length }) : t('specialistInsurance.allHorsesDone', 'Đã xử lý đủ {{total}} ngựa', { total: b.horses.length })}
        </span>
      </div>
      <div className={s.layout}>
        <div className={s.horseColumn}>
          <ul className={s.horseList} aria-label={t('specialistInsurance.horseListLabel', 'Ngựa trong đơn')}>
            {b.horses.map(h => {
              const task = tasks.get(h.horseId)!
              const option = h.insurance.option
              return <li key={h.horseId}>
                <button type="button" className={`${s.horseItem} ${h.horseId === horse?.horseId ? s.horseItemActive : ''}`} aria-pressed={h.horseId === horse?.horseId} disabled={locked && h.horseId !== horse?.horseId} onClick={() => setPicked(h.horseId)}>
                  <span className={s.horseName}>{h.name}<small>{h.microchip}</small></span>
                  <span className={s.horseOption}>{option === 'assistance' ? t('specialistInsurance.reqAssistance', 'Yêu cầu hỗ trợ bảo hiểm') : option === 'personal' ? t('specialistInsurance.hasPersonal', 'Đã có bảo hiểm cá nhân') : option === 'none' ? t('specialistInsurance.noInsurance', 'Không bảo hiểm') : t('specialistInsurance.noChoice', 'Khách chưa chọn')}</span>
                  <span className={`badge badge-${task.tone}`}>{task.label}</span>
                </button>
              </li>
            })}
          </ul>
          {locked && <small className="text-muted">{t('specialistInsurance.selectHorseHint', 'Lưu hoặc hủy thay đổi trước khi chuyển ngựa.')}</small>}
        </div>
        <div className={s.body}>
          {horse && <InsuranceEditor key={`${horse.horseId}-${JSON.stringify(horse.insurance)}`} b={b} by={by} horseId={horse.horseId} insurance={horse.insurance} onDone={onDone} onLock={setLocked} />}
        </div>
      </div>
    </section>
  )
}

function InsuranceEditor({ b, by, horseId, insurance, onDone, onLock }: { b: Booking; by?: string; horseId: string; insurance: Partial<HorseInsurance>; onDone: () => void; onLock: (locked: boolean) => void }) {
  const { t } = useTranslation()
  const initial = { ...insurance, option: insurance.option ?? 'none' } as HorseInsurance
  const [value, setValue] = useState(initial)
  const [baseline, setBaseline] = useState(JSON.stringify(initial))
  const [optionConfirmed, setOptionConfirmed] = useState(!!insurance.option)
  const [savedOptionConfirmed, setSavedOptionConfirmed] = useState(!!insurance.option)
  const [editingConfirmed, setEditingConfirmed] = useState(false)
  const saving = useRef(false)
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [dateValid, setDateValid] = useState(true)
  const [error, setError] = useState('')
  const toast = useToast()
  const [asking, setAsking] = useState(false)
  const [askReason, setAskReason] = useState('')
  const before = insuranceBeforeQuote(b.status)
  const after = insuranceAfterDeposit(b.status)
  // Đang chờ khách bổ sung qua thẻ Bổ sung hồ sơ: chuyên viên chỉ xem cho tới khi khách gửi lại
  const waitingCustomer = before && insuranceRevisionPending(insurance)
  // Chỉ bảo hiểm cá nhân do khách cung cấp mới cần khách bổ sung; hỗ trợ bảo hiểm do công ty thu xếp
  const canAskCustomer = before && !waitingCustomer && insurance.option === 'personal'
  const editable = (before && !waitingCustomer) || (!!insurance.option && insurance.option !== 'none' && after)
  const savedValue = JSON.parse(baseline) as HorseInsurance
  const confirmed = before ? savedValue.reviewed === true : after && savedValue.arranged === true
  const readOnly = confirmed && !editingConfirmed
  const day = new Date(b.departAt).toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' })
  const reviewProblem = insuranceReviewProblem(value, day)
  const policyProblem = insuranceDepartureProblem({ ...value, reviewed: true, arranged: true }, day)
  const dirty = baseline !== JSON.stringify(value) || (!savedOptionConfirmed && optionConfirmed)
  const blocker = useBlocker(dirty || uploading)
  useEffect(() => { onLock(editingConfirmed || dirty || busy || uploading); return () => onLock(false) }, [editingConfirmed, dirty, busy, uploading, onLock])
  useEffect(() => {
    if (!dirty && !uploading) return
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, uploading])
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    if (!uploading && window.confirm(t('specialistInsurance.leaveConfirm', 'Bạn có thay đổi bảo hiểm chưa lưu. Rời trang và bỏ các thay đổi này?'))) blocker.proceed()
    else blocker.reset()
  }, [blocker, uploading, t])
  const optionLabel = (option?: HorseInsurance['option']) => option === 'assistance' ? t('specialistInsurance.reqAssistance', 'Yêu cầu hỗ trợ bảo hiểm') : option === 'personal' ? t('specialistInsurance.hasPersonal', 'Đã có bảo hiểm cá nhân') : t('specialistInsurance.noInsurance', 'Không bảo hiểm')
  const askCustomer = async () => {
    if (busy || !askReason.trim()) return
    setBusy(true); setError('')
    try {
      await bookingsApi.requestInsuranceRevision(b.id, by, askReason, horseId)
      toast(t('specialistInsurance.askSent', 'Đã yêu cầu khách bổ sung bảo hiểm')); setAsking(false); setAskReason(''); onDone()
    } catch (err) { setError(err instanceof Error ? err.message : t('specialistInsurance.askFailed', 'Không gửi được yêu cầu bổ sung.')) }
    finally { setBusy(false) }
  }
  const save = async (confirm: boolean) => {
    if (saving.current || busy || uploading || !editable || readOnly || (!confirm && !dirty) || (confirm && confirmed && !dirty)) return
    setError('')
    const problem = before ? reviewProblem : policyProblem
    if (!optionConfirmed || (confirm && problem)) { setError(problem ?? t('specialistInsurance.confirmWithCustomerWarning', 'Xác nhận lựa chọn với khách trước khi lưu.')); return }
    const next = { ...value, reviewed: before ? confirm && value.option !== 'none' : value.reviewed, arranged: before ? false : confirm }
    saving.current = true
    setBusy(true)
    try {
      const response = await specialistApi.updateInsurance(b.id, horseId, next)
      const accepted = response.horses?.find(h => h.horseId === horseId)?.insurance
      const saved: HorseInsurance = accepted ? {
        ...next, ...accepted,
        documents: accepted.documents ?? [], policyDocuments: accepted.policyDocuments ?? [],
        documentNames: accepted.documentNames ?? {},
      } : next
      invalidateBookingsListCache()
      setValue(saved); setBaseline(JSON.stringify(saved))
      setSavedOptionConfirmed(true); setEditingConfirmed(false)
      toast(confirm ? t('specialistInsurance.confirmedSuccess', 'Đã xác nhận bảo hiểm') : t('specialistInsurance.draftSaved', 'Đã lưu nháp bảo hiểm')); onDone()
    } catch (err) { setError(err instanceof Error ? err.message : t('specialistInsurance.saveFailed', 'Không lưu được bảo hiểm.')) }
    finally { saving.current = false; setBusy(false) }
  }
  return <div className={s.editor}>
    <div className={s.status}>
      <i className="fa-solid fa-shield-halved" />
      <span>{before ? value.reviewed ? t('specialistInsurance.statusConfirmedForQuote', 'Đã xác nhận thông tin bảo hiểm để gửi báo giá chung') : t('specialistInsurance.statusPreparedForQuote', 'Chuẩn bị thông tin trước khi gửi báo giá chung') : after ? value.arranged ? t('specialistInsurance.statusPolicyActive', 'Đã xác nhận hợp đồng có hiệu lực') : t('specialistInsurance.statusNeedPolicyAfterDeposit', 'Bổ sung hợp đồng sau đặt cọc') : t('specialistInsurance.statusSavedByPhase', 'Thông tin đã lưu theo giai đoạn của đơn')}</span>
    </div>
    {waitingCustomer && <div className="alert alert-warning" role="status"><i className="fa-solid fa-hourglass-half" /><div>
      <b>{t('specialistInsurance.waitingCustomer', 'Đang chờ khách bổ sung bảo hiểm')}</b>{insurance.revisionRequestedAt ? ` · ${formatDateTime(insurance.revisionRequestedAt)}` : ''}
      <div>{insurance.revisionReason}</div>
      <small>{t('specialistInsurance.waitingCustomerHint', 'Khách cập nhật trong thẻ Bổ sung hồ sơ của đơn. Khi khách gửi lại, bảo hiểm của ngựa này quay về mục Cần báo giá / kiểm tra.')}</small>
    </div></div>}
    {before && !waitingCustomer && insurance.lastRevision && !insurance.reviewed && <div className="alert alert-info" role="status"><i className="fa-solid fa-rotate-right" /><div>
      <b>{insurance.lastRevision.action === 'switched'
        ? t('specialistInsurance.customerSwitched', 'Khách không bổ sung được, đổi {{from}} → {{to}}', { from: optionLabel(insurance.lastRevision.fromOption), to: optionLabel(insurance.lastRevision.toOption) })
        : t('specialistInsurance.customerSupplemented', 'Khách vừa bổ sung bảo hiểm')}</b>{insurance.lastRevision.at ? ` · ${formatDateTime(insurance.lastRevision.at)}` : ''}
      {insurance.lastRevision.customerReason && <div>{t('specialistInsurance.customerReason', 'Lý do của khách:')} {insurance.lastRevision.customerReason}</div>}
      {insurance.lastRevision.requestReason && <small>{t('specialistInsurance.previousRequest', 'Yêu cầu trước đó:')} {insurance.lastRevision.requestReason}</small>}
    </div></div>}
    {(!editable || readOnly || (after && insurance.option === 'assistance')) && <InsuranceSummary insurance={readOnly ? savedValue : insurance} phase={after && !readOnly ? 'quote' : 'all'} />}
    {!insurance.option && before && (
      <label className="form-group">
        {t('specialistInsurance.confirmedWithCustomer', 'Lựa chọn đã xác nhận với khách')}
        <select className="form-control" disabled={busy || uploading} value={optionConfirmed ? value.option : ''} onChange={e => { setOptionConfirmed(true); setValue({ option: e.target.value as HorseInsurance['option'] }) }}>
          <option value="" disabled>{t('specialistInsurance.selectAfterConfirm', 'Chọn sau khi xác nhận với khách')}</option>
          <option value="none">{t('specialistInsurance.noInsurance', 'Không bảo hiểm')}</option>
          <option value="personal">{t('specialistInsurance.hasPersonal', 'Đã có bảo hiểm cá nhân')}</option>
          <option value="assistance">{t('specialistInsurance.reqAssistance', 'Yêu cầu hỗ trợ bảo hiểm')}</option>
        </select>
      </label>
    )}
    {before && insurance.option && (
      <p>
        <b>{t('specialistInsurance.customerChoice', 'Lựa chọn của khách:')}</b>{' '}
        {value.option === 'assistance' ? t('specialistInsurance.reqAssistance', 'Yêu cầu hỗ trợ bảo hiểm') : value.option === 'personal' ? t('specialistInsurance.hasPersonal', 'Đã có bảo hiểm cá nhân') : t('specialistInsurance.noInsurance', 'Không bảo hiểm')}
      </p>
    )}
    {editable && <>
      {!readOnly && optionConfirmed && value.option !== 'none' && <InsuranceInputs value={value} onChange={next => { setValue({ ...next, ...(before ? { reviewed: false } : { arranged: false }) }); setError('') }} onBusyChange={setUploading} onValidityChange={setDateValid} disabled={busy} freezeQuote={after} lockDocuments={value.option === 'personal'} premium={before && value.option === 'assistance'} />}
      {optionConfirmed && value.option === 'none' && <p>{t('specialistInsurance.noInsuranceDesc', 'Khách chọn không bảo hiểm. Không cần báo giá hoặc hợp đồng.')}</p>}
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      {!readOnly && optionConfirmed && value.option !== 'none' && (before ? reviewProblem : policyProblem) && <p className="form-hint">{before ? reviewProblem : policyProblem}</p>}
      <div className={s.actions}>
        <button type="button" className="btn btn-outline" disabled={busy || uploading || readOnly || !dirty || !optionConfirmed || !dateValid} onClick={() => void save(false)}>{busy ? t('specialistInsurance.saving', 'Đang lưu…') : t('specialistInsurance.saveDraft', 'Lưu nháp')}</button>
        <button type="button" className="btn btn-primary" disabled={busy || uploading || readOnly || (confirmed && !dirty) || !optionConfirmed || !dateValid || !!(before ? reviewProblem : policyProblem)} onClick={() => void save(true)}>{readOnly ? <><i className="fa-solid fa-check" /> {before ? t('specialistInsurance.quoteConfirmed', 'Đã xác nhận') : t('specialistInsurance.policyConfirmed', 'Đã xác nhận hợp đồng')}</> : value.option === 'none' ? t('specialistInsurance.saveChoice', 'Lưu lựa chọn') : before ? value.option === 'personal' ? t('specialistInsurance.confirmCheckContract', 'Xác nhận đã kiểm tra hợp đồng') : t('specialistInsurance.confirmQuote', 'Xác nhận') : t('specialistInsurance.confirmActive', 'Xác nhận hợp đồng có hiệu lực')}</button>
        {readOnly && before && <button type="button" className="btn btn-outline" disabled={busy || uploading} onClick={() => { setEditingConfirmed(true); setError('') }}>{t('specialistInsurance.editQuote', 'Chỉnh sửa báo giá')}</button>}
        {(dirty || editingConfirmed) && <button type="button" className="btn btn-ghost" disabled={busy || uploading} onClick={() => { setValue(savedValue); setOptionConfirmed(savedOptionConfirmed); setEditingConfirmed(false); setError('') }}>{t('specialistInsurance.discard', 'Hủy thay đổi')}</button>}
        {canAskCustomer && !dirty && !asking && <button type="button" className="btn btn-ghost" disabled={busy || uploading} onClick={() => { setAsking(true); setError('') }}><i className="fa-solid fa-rotate-left" /> {t('specialistInsurance.askCustomer', 'Yêu cầu khách bổ sung')}</button>}
      </div>
      {asking && <div className="form-group">
        <label htmlFor={`ask-${horseId}`} className="required">{t('specialistInsurance.askReason', 'Lý do bổ sung (khách sẽ thấy)')}</label>
        <textarea id={`ask-${horseId}`} className="form-control" rows={3} maxLength={500} value={askReason} onChange={e => setAskReason(e.target.value)}
          placeholder={t('specialistInsurance.askPlaceholder', 'VD: Hợp đồng hết hạn trước ngày khởi hành, cần hợp đồng gia hạn.')} />
        <small className="text-muted">{t('specialistInsurance.askHint', 'Khách bổ sung trong thẻ Bổ sung hồ sơ. Nếu không bổ sung được, khách có thể đổi phương án bảo hiểm và phải nêu lý do.')}</small>
        <div className={s.actions}>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => { setAsking(false); setAskReason('') }}>{t('specialistInsurance.askCancel', 'Hủy')}</button>
          <button type="button" className="btn btn-danger" disabled={busy || !askReason.trim()} onClick={() => void askCustomer()}>{t('specialistInsurance.askSend', 'Gửi yêu cầu bổ sung')}</button>
        </div>
      </div>}
    </>}
    {!insurance.option && !before && <p className="text-muted">{t('specialistInsurance.legacyNote', 'Đơn cũ giữ thông tin bảo hiểm theo báo giá đã phát hành.')}</p>}
  </div>
}
