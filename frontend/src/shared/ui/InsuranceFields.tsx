import { useState } from 'react'
import type { HorseInsurance } from '../types/insurance'
import { insuranceLabels } from '../types/insurance'
import { formatVND } from '../lib/format'
import { formatInsuranceMoney, parseInsuranceMoney } from '../lib/insuranceMoney'
import { InsuranceDocuments } from './InsuranceDocuments'
import s from './InsuranceFields.module.css'

export function InsuranceSummary({ insurance, phase = 'all' }: { insurance?: Partial<HorseInsurance>; phase?: 'quote' | 'policy' | 'all' }) {
  if (!insurance?.option) return <p className="text-muted">Bảo hiểm theo dữ liệu đơn cũ; xem báo giá đã phát hành.</p>
  const i = insurance
  if (i.option === 'none') return <p>Khách chọn không bảo hiểm.</p>
  const showPolicy = i.option === 'personal' || phase !== 'quote'
  return <div className={s.summary}>
    <b>{insuranceLabels[i.option!]}</b>
    {i.provider && <div>Đơn vị bảo hiểm: {i.provider}</div>}
    {i.option === 'assistance' && <>
      <div>{i.reviewed && i.premium != null ? `Phí thu hộ: ${formatVND(i.premium)}` : 'Chờ báo giá bên thứ ba'}</div>
      {i.quoteValidUntil && <div>Hạn báo giá bảo hiểm: {i.quoteValidUntil}</div>}
    </>}
    {showPolicy && <>
      {i.option === 'assistance' && <div>{i.arranged ? 'Đã xác nhận thu xếp hợp đồng' : 'Chờ thu xếp hợp đồng sau đặt cọc'}</div>}
      {i.policyNumber && <div>Số hợp đồng / Giấy chứng nhận: {i.policyNumber}</div>}
      {i.validFrom && <div>Hiệu lực: {i.validFrom} – {i.validUntil}</div>}
    </>}
    {i.note && <p>{i.note}</p>}
    <InsuranceDocuments urls={i.documents} names={i.documentNames} label={i.option === 'personal' ? 'Chứng từ bảo hiểm cá nhân' : 'Chứng từ báo giá'} />
    {showPolicy && i.option === 'assistance' && !!i.policyDocuments?.length && <InsuranceDocuments urls={i.policyDocuments} names={i.documentNames} label="Chứng từ hợp đồng" />}
  </div>
}

export function InsuranceInputs({ value, onChange, premium = false, disabled = false, freezeQuote = false, onBusyChange }: { value: HorseInsurance; onChange: (v: HorseInsurance) => void; premium?: boolean; disabled?: boolean; freezeQuote?: boolean; onBusyChange?: (busy: boolean) => void }) {
  const [uploading, setUploading] = useState(false)
  const quoteOnly = value.option === 'assistance' && !freezeQuote
  const policyOnly = value.option === 'assistance' && freezeQuote
  const field = (key: 'provider' | 'policyNumber' | 'validFrom' | 'validUntil' | 'quoteValidUntil', label: string, type = 'text') => <label className="form-group">{label}<input className="form-control" type={type} disabled={freezeQuote && key === 'provider'} value={value[key] ?? ''} onChange={e => onChange({ ...value, [key]: e.target.value || undefined })} /></label>
  return <fieldset className={s.fields} disabled={disabled || uploading}>
    <div className={s.formGrid}>
      {!policyOnly && field('provider', 'Đơn vị bảo hiểm')}
      {premium && <label className="form-group">Phí thu hộ (VNĐ)<input className="form-control" inputMode="numeric" autoComplete="off" value={formatInsuranceMoney(value.premium)} onChange={e => {
        const el = e.currentTarget
        const digitsBefore = el.value.slice(0, el.selectionStart ?? el.value.length).replace(/[^0-9]/g, '').length
        const next = parseInsuranceMoney(el.value); onChange({ ...value, premium: next })
        requestAnimationFrame(() => { let digits = 0; let pos = 0; while (pos < el.value.length && digits < digitsBefore) { if (/[0-9]/.test(el.value[pos])) digits++; pos++ } el.setSelectionRange(pos, pos) })
      }} /><small>Phí bên bảo hiểm báo; công ty thu hộ.</small></label>}
      {quoteOnly && field('quoteValidUntil', 'Báo giá bên thứ ba có hiệu lực đến (nếu có)', 'date')}
      {!quoteOnly && <>
        {field('policyNumber', 'Số hợp đồng / Giấy chứng nhận bảo hiểm')}
        {field('validFrom', 'Hiệu lực từ', 'date')}{field('validUntil', 'Hiệu lực đến', 'date')}
      </>}
    </div>
    {!policyOnly && <label className="form-group">Ghi chú<textarea className="form-control" rows={3} value={value.note ?? ''} onChange={e => onChange({ ...value, note: e.target.value })} /></label>}
    <InsuranceDocuments label={policyOnly ? 'Chứng từ hợp đồng' : quoteOnly ? 'Chứng từ báo giá' : 'Chứng từ bảo hiểm cá nhân'} urls={policyOnly ? value.policyDocuments : value.documents} names={value.documentNames} disabled={disabled}
      onBusyChange={busy => { setUploading(busy); onBusyChange?.(busy) }}
      onChange={(urls, names) => onChange({ ...value, [policyOnly ? 'policyDocuments' : 'documents']: urls, documentNames: names })} />
    <small className="text-muted">Thay đổi và gỡ chứng từ có hiệu lực với hồ sơ khi lưu.</small>
  </fieldset>
}
export function CustomerInsuranceFields({ value, option, onChange, onBusyChange }: { value?: HorseInsurance; option: string; onChange: (v: HorseInsurance) => void; onBusyChange?: (busy: boolean) => void }) {
  const [uploading, setUploading] = useState(false)
  return <section className={s.customerInsurance}>
    <h4 id="insurance-option-heading">Bảo hiểm chuyến đi</h4>
    <p>Công ty hỗ trợ liên hệ và thu hộ phí bảo hiểm do bên thứ ba cung cấp.</p>
    <div className={s.insuranceOptions} role="radiogroup" aria-labelledby="insurance-option-heading">
      {Object.entries(insuranceLabels).map(([key, label]) => (
        <label key={key} className={s.insuranceOption}>
          <input type="radio" name="insurance-option" disabled={uploading} checked={option === key} onChange={() => onChange({ option: key as HorseInsurance['option'] })} />
          <span>{label}</span>
        </label>
      ))}
    </div>
    {option === 'personal' && value && <InsuranceInputs value={value} onChange={onChange} onBusyChange={busy => { setUploading(busy); onBusyChange?.(busy) }} />}
    {option === 'assistance' && <p>Phí và chứng từ được gửi cùng báo giá vận chuyển. Chưa có mức phí tại bước này.</p>}
  </section>
}
