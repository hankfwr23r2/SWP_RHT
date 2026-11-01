import { useState } from 'react'
import type { HorseInsurance } from '../types/insurance'
import { insuranceLabels } from '../types/insurance'
import { fileApi } from '../api/files'
import { formatVND } from '../lib/format'

export function InsuranceSummary({ insurance }: { insurance?: Partial<HorseInsurance> }) {
  if (!insurance?.option) return <span>Bảo hiểm theo dữ liệu đơn cũ</span>
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
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const field = (key: 'provider' | 'policyNumber' | 'validFrom' | 'validUntil' | 'note', label: string, type = 'text') => <label className="form-group">{label}<input className="form-control" type={type} value={value[key] ?? ''} disabled={disabled || (freezeQuote && key === 'provider')} onChange={e => onChange({ ...value, [key]: e.target.value })} /></label>
  return <div>
    {field('provider', 'Đơn vị bảo hiểm')}
    {premium && <label className="form-group">Phí thu hộ (VNĐ)<input className="form-control" type="number" min="1" step="1" value={value.premium ?? ''} disabled={disabled} onChange={e => onChange({ ...value, premium: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>}
    {field('policyNumber', 'Số hợp đồng (bổ sung sau khi thu xếp nếu đang chờ báo giá)')}
    {field('validFrom', 'Hiệu lực từ', 'date')}{field('validUntil', 'Hiệu lực đến', 'date')}
    {field('note', 'Ghi chú')}
    <label className="form-group">Chứng từ (ảnh/PDF, tối đa 10 tệp)<input type="file" accept="image/*,application/pdf" disabled={uploading || disabled || (value.documents?.length ?? 0) >= 10} onChange={async e => {
      const file = e.target.files?.[0]; if (!file) return
      setUploading(true); onBusyChange?.(true); setError('')
      try { const uploaded = await fileApi.upload(file, 'insurance'); onChange({ ...value, documents: [...(value.documents ?? []), uploaded.url] }) }
      catch (err) { setError(err instanceof Error ? err.message : 'Không tải được chứng từ') }
      finally { setUploading(false); onBusyChange?.(false) }
    }} /></label>
    {uploading && <p>Đang tải chứng từ…</p>}{error && <p className="form-error">{error}</p>}
    {value.documents?.map((url, index) => <div key={url}><a href={url} target="_blank" rel="noreferrer">Chứng từ {index + 1}</a>{!disabled && <button type="button" className="btn btn-ghost" onClick={() => onChange({ ...value, documents: value.documents?.filter(u => u !== url) })}>Gỡ</button>}</div>)}
  </div>
}
export function CustomerInsuranceFields({ value, option, onChange, onBusyChange }: { value?: HorseInsurance; option: string; onChange: (v: HorseInsurance) => void; onBusyChange?: (busy: boolean) => void }) {
  const [uploading, setUploading] = useState(false)
  return <section><h4>Bảo hiểm chuyến đi</h4><p>Công ty hỗ trợ liên hệ và thu hộ phí bảo hiểm do bên thứ ba cung cấp.</p>
    {Object.entries(insuranceLabels).map(([key, label]) => <label key={key} style={{ display: 'block', marginBottom: 12 }}><input type="radio" name="insurance-option" disabled={uploading} checked={option === key} onChange={() => onChange({ option: key as HorseInsurance['option'] })} /> {label}</label>)}
    {option === 'personal' && value && <InsuranceInputs value={value} onChange={onChange} onBusyChange={busy => { setUploading(busy); onBusyChange?.(busy) }} />}
    {option === 'assistance' && <p>Phí và chứng từ được gửi cùng báo giá vận chuyển. Chưa có mức phí tại bước này.</p>}
  </section>
}
