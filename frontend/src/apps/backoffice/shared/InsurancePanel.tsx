import { useState } from 'react'
import type { Booking } from '@shared/types/booking'
import { insuranceReviewProblem, type HorseInsurance } from '@shared/types/insurance'
import { InsuranceInputs, InsuranceSummary } from '@shared/ui/InsuranceFields'
import { specialistApi } from '@shared/api/specialist'
import { useToast } from '@shared/ui/toast'

export function InsurancePanel({ b, onDone }: { b: Booking; onDone: () => void }) {
  return <section className="card"><div className="card-header"><h3>Bảo hiểm bên thứ ba</h3></div>
    <p>Hoàn tất báo giá cùng hồ sơ thẩm định. Sau đặt cọc, bổ sung hợp đồng và xác nhận thu xếp trước khi vận chuyển.</p>
    {b.horses.map(h => <InsuranceEditor key={`${h.horseId}-${JSON.stringify(h.insurance)}`} b={b} horseId={h.horseId} name={h.name} insurance={h.insurance} onDone={onDone} />)}
  </section>
}
function InsuranceEditor({ b, horseId, name, insurance, onDone }: { b: Booking; horseId: string; name: string; insurance: Partial<HorseInsurance>; onDone: () => void }) {
  const [value, setValue] = useState<HorseInsurance>({ ...insurance, option: insurance.option ?? 'none' })
  const [optionConfirmed, setOptionConfirmed] = useState(!!insurance.option)
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const toast = useToast()
  const before = ['under_review', 'pending_commercial', 'resubmission_requested'].includes(b.status)
  const after = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup'].includes(b.status)
  const editable = before || (!!insurance.option && insurance.option !== 'none' && after)
  const reviewProblem = insuranceReviewProblem(value, new Date(b.departAt).toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' }))
  return <div style={{ padding: 16, borderTop: '1px solid var(--border)' }}><h4>{name}</h4>
    <div style={{ marginBottom: 12 }}><InsuranceSummary insurance={insurance} /></div>
    {!insurance.option && before && <label style={{ display: 'block' }}>Lựa chọn đã xác nhận với khách<select className="form-control" value={optionConfirmed ? value.option : ''} onChange={e => { setOptionConfirmed(true); setValue({ option: e.target.value as HorseInsurance['option'] }) }}><option value="" disabled>Chọn sau khi xác nhận với khách</option><option value="none">Không bảo hiểm</option><option value="personal">Đã có bảo hiểm cá nhân</option><option value="assistance">Yêu cầu hỗ trợ bảo hiểm</option></select></label>}
    {editable && <>
      {optionConfirmed && value.option !== 'none' && <InsuranceInputs value={value} onChange={next => setValue(before ? { ...next, reviewed: false } : next)} onBusyChange={setUploading} freezeQuote={after} premium={before && value.option === 'assistance'} />}
      {before && optionConfirmed && value.option !== 'none' && <label><input type="checkbox" disabled={!!reviewProblem || uploading} checked={!!value.reviewed} onChange={e => setValue({ ...value, reviewed: e.target.checked })} /> {value.option === 'assistance' ? 'Tôi đã nhận báo giá bên thứ ba và kiểm tra phí thu hộ' : 'Tôi đã kiểm tra hợp đồng cá nhân và thời hạn bảo hiểm'}</label>}
      {after && value.option === 'assistance' && <label><input type="checkbox" checked={!!value.arranged} onChange={e => setValue({ ...value, arranged: e.target.checked })} /> Đã thu xếp hợp đồng, xác nhận thời hạn có hiệu lực</label>}
      <div><button type="button" className="btn btn-primary" disabled={busy || uploading || !optionConfirmed} onClick={async () => {
        setBusy(true)
        try { await specialistApi.updateInsurance(b.id, horseId, value); toast('Đã lưu bảo hiểm'); onDone() }
        catch (e) { toast(e instanceof Error ? e.message : 'Không lưu được', 'error') }
        finally { setBusy(false) }
      }}>{busy ? 'Đang lưu…' : 'Lưu bảo hiểm'}</button></div>
    </>}
    {before && optionConfirmed && value.option !== 'none' && reviewProblem && <p>{reviewProblem}</p>}
    {!insurance.option && <p>{before ? 'Đơn cũ chưa có lựa chọn bảo hiểm theo mô hình mới. Xác nhận với khách trước khi lưu; hệ thống không tự chọn thay khách.' : 'Thông tin bảo hiểm của đơn này được giữ theo báo giá cũ.'}</p>}
  </div>
}
