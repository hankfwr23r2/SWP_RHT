import { useEffect, useRef, useState } from 'react'
import { Link, useBlocker } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { Booking } from '@shared/types/booking'
import { insuranceAfterDeposit, insuranceBeforeQuote, insuranceDepartureProblem, insuranceReviewProblem, type HorseInsurance } from '@shared/types/insurance'
import { InsuranceInputs, InsuranceSummary } from '@shared/ui/InsuranceFields'
import { specialistApi } from '@shared/api/specialist'
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

export function InsurancePanel({ b, onDone }: { b: Booking; onDone: () => void }) {
  const { t } = useTranslation()
  const [picked, setPicked] = useState(b.horses[0]?.horseId ?? '')
  const [locked, setLocked] = useState(false)
  const horse = b.horses.find(h => h.horseId === picked) ?? b.horses[0]
  return (
    <section className="card">
      <div className="card-header">
        <h3>{insuranceBeforeQuote(b.status) ? t('specialistInsurance.quoteAndCheck', 'Báo giá và kiểm tra bảo hiểm') : t('specialistInsurance.insuranceByBooking', 'Bảo hiểm theo đơn')}</h3>
      </div>
      <div className={s.body}>
        <label className="form-group">
          {t('specialistInsurance.selectHorse', 'Chọn ngựa ({{count}})', { count: b.horses.length })}
          <select className="form-control" value={horse?.horseId ?? ''} disabled={locked} onChange={e => setPicked(e.target.value)}>
            {b.horses.map(h => <option key={h.horseId} value={h.horseId}>{h.name} · {h.microchip}</option>)}
          </select>
          <small>{t('specialistInsurance.selectHorseHint', 'Lưu hoặc hủy thay đổi trước khi chuyển ngựa.')}</small>
        </label>
        {horse && <InsuranceEditor key={`${horse.horseId}-${JSON.stringify(horse.insurance)}`} b={b} horseId={horse.horseId} insurance={horse.insurance} onDone={onDone} onLock={setLocked} />}
      </div>
    </section>
  )
}

function InsuranceEditor({ b, horseId, insurance, onDone, onLock }: { b: Booking; horseId: string; insurance: Partial<HorseInsurance>; onDone: () => void; onLock: (locked: boolean) => void }) {
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
  const [error, setError] = useState('')
  const toast = useToast()
  const before = insuranceBeforeQuote(b.status)
  const after = insuranceAfterDeposit(b.status)
  const editable = before || (!!insurance.option && insurance.option !== 'none' && after)
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
      {!readOnly && optionConfirmed && value.option !== 'none' && <InsuranceInputs value={value} onChange={next => { setValue({ ...next, ...(before ? { reviewed: false } : { arranged: false }) }); setError('') }} onBusyChange={setUploading} disabled={busy} freezeQuote={after} premium={before && value.option === 'assistance'} />}
      {optionConfirmed && value.option === 'none' && <p>{t('specialistInsurance.noInsuranceDesc', 'Khách chọn không bảo hiểm. Không cần báo giá hoặc hợp đồng.')}</p>}
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      {!readOnly && optionConfirmed && value.option !== 'none' && (before ? reviewProblem : policyProblem) && <p className="form-hint">{before ? reviewProblem : policyProblem}</p>}
      <div className={s.actions}>
        <button type="button" className="btn btn-outline" disabled={busy || uploading || readOnly || !dirty || !optionConfirmed} onClick={() => void save(false)}>{busy ? t('specialistInsurance.saving', 'Đang lưu…') : t('specialistInsurance.saveDraft', 'Lưu nháp')}</button>
        <button type="button" className="btn btn-primary" disabled={busy || uploading || readOnly || (confirmed && !dirty) || !optionConfirmed || !!(before ? reviewProblem : policyProblem)} onClick={() => void save(true)}>{readOnly ? <><i className="fa-solid fa-check" /> {before ? t('specialistInsurance.quoteConfirmed', 'Đã xác nhận báo giá') : t('specialistInsurance.policyConfirmed', 'Đã xác nhận hợp đồng')}</> : value.option === 'none' ? t('specialistInsurance.saveChoice', 'Lưu lựa chọn') : before ? value.option === 'personal' ? t('specialistInsurance.confirmCheckContract', 'Xác nhận đã kiểm tra hợp đồng') : t('specialistInsurance.confirmQuote', 'Xác nhận báo giá bảo hiểm') : t('specialistInsurance.confirmActive', 'Xác nhận hợp đồng có hiệu lực')}</button>
        {readOnly && before && <button type="button" className="btn btn-outline" disabled={busy || uploading} onClick={() => { setEditingConfirmed(true); setError('') }}>{t('specialistInsurance.editQuote', 'Chỉnh sửa báo giá')}</button>}
        {(dirty || editingConfirmed) && <button type="button" className="btn btn-ghost" disabled={busy || uploading} onClick={() => { setValue(savedValue); setOptionConfirmed(savedOptionConfirmed); setEditingConfirmed(false); setError('') }}>{t('specialistInsurance.discard', 'Hủy thay đổi')}</button>}
      </div>
    </>}
    {!insurance.option && !before && <p className="text-muted">{t('specialistInsurance.legacyNote', 'Đơn cũ giữ thông tin bảo hiểm theo báo giá đã phát hành.')}</p>}
  </div>
}
