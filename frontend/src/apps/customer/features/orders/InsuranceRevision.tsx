// Bổ sung bảo hiểm trong thẻ Bổ sung hồ sơ: mặc định bổ sung trên loại đang có,
// chỉ khi không bổ sung được khách mới đổi sang loại khác và phải nêu lý do.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { customerInsuranceStatus, type HorseInsurance, type InsuranceResubmission } from '@shared/types/insurance'
import { revisionProblem } from './insuranceRevisionRules'
import { InsuranceInputs } from '@shared/ui/InsuranceFields'
import { Modal } from '@shared/ui/Modal'
import { PolicyLink } from '@shared/ui/PolicyLink'

type Option = HorseInsurance['option']

export function InsuranceRevisionModal({ horseName, horseId, current, draft, onSave, onClose }: {
  horseName: string
  horseId: string
  current: Partial<HorseInsurance> & { option: Option }
  draft?: InsuranceResubmission
  onSave: (draft: InsuranceResubmission) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const keep = (): HorseInsurance => current.option === 'personal'
    ? { option: 'personal', provider: current.provider, policyNumber: current.policyNumber, validFrom: current.validFrom, validUntil: current.validUntil, note: current.note, documents: current.documents ?? [], documentNames: current.documentNames ?? {} }
    : { option: current.option }
  const [value, setValue] = useState<HorseInsurance>(draft?.insurance ?? keep())
  const [switching, setSwitching] = useState(!!draft && draft.insurance.option !== current.option)
  const [switchReason, setSwitchReason] = useState(draft?.switchReason ?? '')
  const [liability, setLiability] = useState(!!draft?.limitedLiabilityAccepted)
  const [busy, setBusy] = useState(false)
  const [datesValid, setDatesValid] = useState(true)
  const [submitted, setSubmitted] = useState(false)
  const label = (option: Option) => option === 'personal' ? t('insuranceRevision.optionPersonal', 'Bảo hiểm cá nhân')
    : option === 'assistance' ? t('insuranceRevision.optionAssistance', 'Hỗ trợ bảo hiểm') : t('insuranceRevision.optionNone', 'Không bảo hiểm')
  const others = (['personal', 'assistance', 'none'] as const).filter(o => o !== current.option)
  const next: InsuranceResubmission = {
    horseId,
    insurance: value,
    switchReason: value.option !== current.option ? switchReason.trim() : undefined,
    limitedLiabilityAccepted: value.option === 'none' ? liability : undefined,
  }
  const problem = revisionProblem(next, current.option) ?? (datesValid ? undefined : 'personalDates')
  const problemText: Record<string, string> = {
    switchReason: t('insuranceRevision.needSwitchReason', 'Nêu lý do bạn không bổ sung được bảo hiểm hiện tại.'),
    personalFields: t('insuranceRevision.needPersonalFields', 'Nhập đơn vị bảo hiểm và số hợp đồng.'),
    personalDates: t('insuranceRevision.needPersonalDates', 'Nhập thời hạn bảo hiểm hợp lệ.'),
    personalDocs: t('insuranceRevision.needPersonalDocs', 'Tải ít nhất một chứng từ bảo hiểm cá nhân.'),
    liability: t('insuranceRevision.needLiability', 'Đồng ý Điều khoản Trách nhiệm Hạn chế để chọn không bảo hiểm.'),
  }
  const startSwitch = () => { setSwitching(true); setValue({ option: others[0] }); setSubmitted(false) }
  const backToSupplement = () => { setSwitching(false); setValue(keep()); setSwitchReason(''); setLiability(false); setSubmitted(false) }
  const save = () => { setSubmitted(true); if (!problem && !busy) onSave(next) }

  return (
    <Modal wide title={t('insuranceRevision.modalTitle', 'Bổ sung bảo hiểm · {{name}}', { name: horseName })} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-ghost" onClick={onClose}>{t('insuranceRevision.cancel', 'Đóng')}</button>
        <button type="button" className="btn btn-primary" disabled={busy} onClick={save}>{t('insuranceRevision.saveDraft', 'Lưu cập nhật')}</button>
      </>}>
      <div className="alert alert-warning" role="status">
        <i className="fa-solid fa-file-circle-exclamation" aria-hidden="true" />
        <div><b>{t('insuranceRevision.requestReason', 'Chuyên viên yêu cầu:')}</b> {current.revisionReason}</div>
      </div>

      {!switching && <>
        <p><b>{t('insuranceRevision.currentOption', 'Phương án hiện tại:')}</b> {label(current.option)}</p>
        {current.option === 'personal' && <InsuranceInputs audience="customer" value={value} onChange={setValue} onBusyChange={setBusy} onValidityChange={setDatesValid} />}
        <button type="button" className="btn btn-ghost btn-sm" onClick={startSwitch}>
          <i className="fa-solid fa-right-left" aria-hidden="true" /> {t('insuranceRevision.cannotSupplement', 'Không bổ sung được? Đổi phương án bảo hiểm')}
        </button>
      </>}

      {switching && <>
        <p className="form-hint">{t('insuranceRevision.switchHint', 'Chỉ đổi phương án khi bạn không thể bổ sung như chuyên viên yêu cầu. Chuyên viên sẽ kiểm tra lại từ đầu.')}</p>
        <div className="form-group" role="radiogroup" aria-label={t('insuranceRevision.newOption', 'Phương án mới')}>
          <label>{t('insuranceRevision.newOption', 'Phương án mới')}</label>
          {others.map(option => (
            <label key={option} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input type="radio" name={`insurance-switch-${horseId}`} disabled={busy} checked={value.option === option}
                onChange={() => { setValue({ option }); setLiability(false) }} />
              {label(option)}
            </label>
          ))}
        </div>
        <div className="form-group">
          <label htmlFor={`switch-reason-${horseId}`} className="required">{t('insuranceRevision.switchReason', 'Lý do không bổ sung được')}</label>
          <textarea id={`switch-reason-${horseId}`} className="form-control" rows={2} maxLength={500} value={switchReason}
            placeholder={t('insuranceRevision.switchReasonPlaceholder', 'VD: Hợp đồng cá nhân đã hết hạn, chưa gia hạn kịp.')}
            onChange={e => setSwitchReason(e.target.value)} />
        </div>
        {value.option === 'personal' && <InsuranceInputs audience="customer" value={value} onChange={setValue} onBusyChange={setBusy} onValidityChange={setDatesValid} />}
        {value.option === 'assistance' && <p>{t('insuranceRevision.assistanceInfo', 'Công ty liên hệ bên bảo hiểm và gửi phí cùng báo giá vận chuyển. Chưa có mức phí tại bước này.')}</p>}
        {value.option === 'none' && (
          <label style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
            <input type="checkbox" checked={liability} onChange={e => setLiability(e.target.checked)} />
            <span>{t('insuranceRevision.liabilityPrefix', 'Tôi hiểu và đồng ý')} <PolicyLink doc="liability">{t('insuranceRevision.liabilityTerms', 'Điều khoản Trách nhiệm Hạn chế')}</PolicyLink> {t('insuranceRevision.liabilitySuffix', 'của nhà xe cho {{name}}.', { name: horseName })}</span>
          </label>
        )}
        <button type="button" className="btn btn-ghost btn-sm" onClick={backToSupplement}>
          <i className="fa-solid fa-arrow-left" aria-hidden="true" /> {t('insuranceRevision.backToSupplement', 'Quay lại bổ sung')}
        </button>
      </>}

      {submitted && problem && <div className="form-error" role="alert">{problemText[problem]}</div>}
    </Modal>
  )
}

// Trạng thái bảo hiểm từng ngựa trong tab Bảo hiểm: khách biết đang ở bước nào và bước tiếp theo
export function CustomerInsuranceStatusLine({ status, insurance, hasQuote }: { status: string; insurance?: Partial<HorseInsurance>; hasQuote: boolean }) {
  const { t } = useTranslation()
  const state = customerInsuranceStatus(status, insurance, hasQuote)
  const view: Record<typeof state, { tone: string; label: string; hint: string }> = {
    legacy: { tone: 'muted', label: t('insuranceRevision.stLegacy', 'Theo báo giá cũ'), hint: t('insuranceRevision.hintLegacy', 'Xem thông tin bảo hiểm trong báo giá đã phát hành.') },
    none: { tone: 'muted', label: t('insuranceRevision.stNone', 'Không bảo hiểm'), hint: t('insuranceRevision.hintNone', 'Ngựa vận chuyển theo Điều khoản Trách nhiệm Hạn chế.') },
    revision: { tone: 'danger', label: t('insuranceRevision.stRevision', 'Cần bạn bổ sung'), hint: t('insuranceRevision.hintRevision', 'Mở thẻ Bổ sung hồ sơ phía trên để cập nhật bảo hiểm.') },
    reviewing: { tone: 'warning', label: t('insuranceRevision.stReviewing', 'Đang kiểm tra'), hint: t('insuranceRevision.hintReviewing', 'Chuyên viên đang kiểm tra hợp đồng bảo hiểm của bạn.') },
    quoting: { tone: 'warning', label: t('insuranceRevision.stQuoting', 'Đang lấy báo giá'), hint: t('insuranceRevision.hintQuoting', 'Phí bảo hiểm sẽ có trong báo giá vận chuyển.') },
    confirmed: { tone: 'success', label: t('insuranceRevision.stConfirmed', 'Đã xác nhận'), hint: insurance?.option === 'assistance' ? t('insuranceRevision.hintConfirmedAssistance', 'Phí bảo hiểm đã có trong báo giá. Hợp đồng được thu xếp sau khi đặt cọc.') : t('insuranceRevision.hintConfirmedPersonal', 'Hợp đồng bảo hiểm cá nhân đã được kiểm tra.') },
    awaitingPolicy: { tone: 'warning', label: t('insuranceRevision.stAwaitingPolicy', 'Đang thu xếp hợp đồng'), hint: t('insuranceRevision.hintAwaitingPolicy', 'Công ty đang hoàn tất hợp đồng trước ngày khởi hành.') },
    active: { tone: 'success', label: t('insuranceRevision.stActive', 'Hợp đồng có hiệu lực'), hint: t('insuranceRevision.hintActive', 'Ngựa được bảo hiểm cho chuyến đi.') },
  }
  const v = view[state]
  return (
    <p style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center', margin: '4px 0 8px' }}>
      <span className={`badge badge-${v.tone}`}>{v.label}</span>
      <span className="text-muted">{state === 'revision' && insurance?.revisionReason ? `${insurance.revisionReason}. ` : ''}{v.hint}</span>
    </p>
  )
}
