import { useState } from 'react'
import type { Booking } from '@shared/types/booking'
import type { HorseInsurance } from '@shared/types/insurance'
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
  const [busy, setBusy] = useState(false)
  const [uploading, setUploading] = useState(false)
  const toast = useToast()
  const before = ['under_review', 'pending_commercial', 'resubmission_requested'].includes(b.status)
  const after = ['waybill_issued', 'clearance_in_progress', 'clearance_done', 'ready_for_pickup', 'en_route_to_pickup'].includes(b.status)
  const editable = before || (!!insurance.option && after)
  return <div style={{ padding: 16, borderTop: '1px solid var(--border)' }}><h4>{name}</h4>
    <InsuranceSummary insurance={insurance} />
    {!insurance.option && before && <label>Xác nhận lại lựa chọn với khách<select className="form-control" value={value.option} onChange={e => setValue({ option: e.target.value as HorseInsurance['option'] })}><option value="none">Không bảo hiểm</option><option value="personal">Đã có bảo hiểm cá nhân</option><option value="assistance">Yêu cầu hỗ trợ bảo hiểm</option></select></label>}
    {editable && <>
      {value.option !== 'none' && <InsuranceInputs value={value} onChange={setValue} onBusyChange={setUploading} freezeQuote={after} premium={before && value.option === 'assistance'} />}
      {before && <label><input type="checkbox" checked={!!value.reviewed} onChange={e => setValue({ ...value, reviewed: e.target.checked })} /> Đã kiểm tra / hoàn tất báo giá bảo hiểm</label>}
      {after && value.option === 'assistance' && <label><input type="checkbox" checked={!!value.arranged} onChange={e => setValue({ ...value, arranged: e.target.checked })} /> Đã thu xếp hợp đồng, xác nhận thời hạn có hiệu lực</label>}
      <div><button type="button" className="btn btn-primary" disabled={busy || uploading} onClick={async () => {
        setBusy(true)
        try { await specialistApi.updateInsurance(b.id, horseId, value); toast('Đã lưu bảo hiểm'); onDone() }
        catch (e) { toast(e instanceof Error ? e.message : 'Không lưu được', 'error') }
        finally { setBusy(false) }
      }}>{busy ? 'Đang lưu…' : 'Lưu bảo hiểm'}</button></div>
    </>}
    {!insurance.option && <p className="form-error">Đơn cũ chưa khai báo theo mô hình bảo hiểm mới; cần xác nhận lại trước báo giá.</p>}
  </div>
}
