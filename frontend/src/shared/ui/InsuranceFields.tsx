import { useId, useState } from 'react'
import type { HorseInsurance } from '../types/insurance'
import { insuranceLabels } from '../types/insurance'
import { fileApi } from '../api/files'
import { formatVND } from '../lib/format'
import s from './InsuranceFields.module.css'

export function InsuranceSummary({ insurance }: { insurance?: Partial<HorseInsurance> }) {
  if (!insurance?.option) return <span>Bảo hiểm theo dữ liệu đơn cũ; xem thông tin trong báo giá đã phát hành</span>
  return <div><b>{insuranceLabels[insurance.option]}</b>
    {insurance.provider && <div>Đơn vị: {insurance.provider}</div>}
    {insurance.policyNumber && <div>Hợp đồng: {insurance.policyNumber}</div>}
    {insurance.validFrom && <div>Thời hạn: {insurance.validFrom} – {insurance.validUntil}</div>}
    {insurance.option === 'assistance' && <div>{insurance.reviewed && insurance.premium != null ? `Phí thu hộ: ${formatVND(insurance.premium)}` : 'Chờ báo giá bên thứ ba'}</div>}
    {insurance.option === 'assistance' && <div>{insurance.arranged ? 'Đã xác nhận thu xếp hợp đồng' : 'Chưa xác nhận hợp đồng có hiệu lực'}</div>}
    {insurance.note && <p>{insurance.note}</p>}
    {insurance.documents?.map((url, index) => <div key={url}><a href={url} target="_blank" rel="noreferrer">Chứng từ {index + 1}</a></div>)}
  </div>
}

export function InsuranceInputs({ value, onChange, premium = false, disabled = false, freezeQuote = false, onBusyChange }: { value: HorseInsurance; onChange: (v: HorseInsurance) => void; premium?: boolean; disabled?: boolean; freezeQuote?: boolean; onBusyChange?: (busy: boolean) => void }) {
  const uploadId = useId()
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const field = (key: 'provider' | 'policyNumber' | 'validFrom' | 'validUntil' | 'note', label: string, type = 'text') => <label className="form-group">{label}<input className="form-control" type={type} value={value[key] ?? ''} disabled={disabled || (freezeQuote && key === 'provider')} onChange={e => onChange({ ...value, [key]: e.target.value })} /></label>
  const uploadDisabled = uploading || disabled || (value.documents?.length ?? 0) >= 10
  return <div>
    {field('provider', 'Đơn vị bảo hiểm')}
    {premium && <label className="form-group">Phí thu hộ (VNĐ)<input className="form-control" type="number" min="1" step="1" value={value.premium ?? ''} disabled={disabled} onChange={e => onChange({ ...value, premium: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>}
    {field('policyNumber', 'Số hợp đồng (bổ sung sau khi thu xếp nếu đang chờ báo giá)')}
    {field('validFrom', 'Hiệu lực từ', 'date')}{field('validUntil', 'Hiệu lực đến', 'date')}
    {field('note', 'Ghi chú')}
    <div className={`form-group ${s.uploadField}`}>
      <label className={s.uploadLabel} htmlFor={uploadId}>Chứng từ (ảnh/PDF, tối đa 10 tệp)</label>
      <input id={uploadId} className={s.uploadInput} type="file" accept="image/*,application/pdf" disabled={uploadDisabled} aria-label="Chọn chứng từ bảo hiểm" onChange={async e => {
        const file = e.currentTarget.files?.[0]
        e.currentTarget.value = ''
        if (!file) return
        setUploading(true); onBusyChange?.(true); setError('')
        try { const uploaded = await fileApi.upload(file, 'insurance'); onChange({ ...value, documents: [...(value.documents ?? []), uploaded.url] }) }
        catch (err) { setError(err instanceof Error ? err.message : 'Không tải được chứng từ') }
        finally { setUploading(false); onBusyChange?.(false) }
      }} />
      <label htmlFor={uploadId} className={`${s.uploadDrop} ${uploadDisabled ? s.uploadDisabled : ''}`}>
        <i className={`fa-solid ${uploading ? 'fa-spinner' : 'fa-cloud-arrow-up'} ${uploading ? 'fa-spin' : ''}`} aria-hidden="true" />
        <span><b>{uploading ? 'Đang tải lên' : 'Chọn tệp từ máy'}</b> {!uploading && '(Ảnh hoặc PDF)'}</span>
      </label>
      <span className={s.uploadHint}>{value.documents?.length ?? 0}/10 chứng từ đã tải lên</span>
    </div>
    {uploading && <p>Đang tải chứng từ…</p>}{error && <p className="form-error">{error}</p>}
    {value.documents?.map((url, index) => <div key={url}><a href={url} target="_blank" rel="noreferrer">Chứng từ {index + 1}</a>{!disabled && <button type="button" className="btn btn-ghost" onClick={() => onChange({ ...value, documents: value.documents?.filter(u => u !== url) })}>Gỡ</button>}</div>)}
  </div>
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
