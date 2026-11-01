import { useState } from 'react'
import type { Booking } from '@shared/types/booking'
import apiClient from '@shared/api/client'
import { formatVND } from '@shared/lib/format'
import { useToast } from '@shared/ui/toast'
export function InsuranceReconciliation({ b, onDone }: { b: Booking; onDone: () => void }) {
  const [transferredAmount, setTransferred] = useState(0)
  const [providerRefundAmount, setProviderRefund] = useState(0)
  const [reference, setReference] = useState('')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const toast = useToast()
  if (!b.cancellation?.insuranceCollected) return null
  const collected = b.cancellation.insuranceCollected
  const customerRefundAmount = Math.min(collected, Math.max(0, collected - transferredAmount + providerRefundAmount))
  return <section className="card"><h3>Đối soát bảo hiểm thu hộ · {b.id}</h3><p>Đã thu: {formatVND(collected)} · {b.cancellation.insuranceReconciliationPending ? 'Chờ đối soát' : 'Đã đối soát'}</p>
    {b.cancellation.insuranceReconciliationPending && <>
      <label>Đã chuyển bên thứ ba (VNĐ)<input className="form-control" type="number" min="0" value={transferredAmount} onChange={e => setTransferred(Number(e.target.value))} /></label>
      <label>Bên thứ ba đã hoàn (VNĐ)<input className="form-control" type="number" min="0" value={providerRefundAmount} onChange={e => setProviderRefund(Number(e.target.value))} /></label>
      <p>Cần hoàn khách: <b>{formatVND(customerRefundAmount)}</b>. Thực hiện hoàn bên ngoài rồi ghi nhận tại đây.</p>
      <label>Mã đối soát / giao dịch hoàn<input className="form-control" value={reference} onChange={e => setReference(e.target.value)} /></label>
      <label>Căn cứ đối soát / chứng từ<input className="form-control" value={note} onChange={e => setNote(e.target.value)} /></label>
      <button className="btn btn-primary" disabled={busy || !reference.trim() || !note.trim()} onClick={async () => {
        setBusy(true)
        try { await apiClient.post(`/manager/bookings/${b.id}/insurance-reconciliation`, { transferredAmount, providerRefundAmount, customerRefundAmount, reference, note }); toast('Đã ghi nhận đối soát'); onDone() }
        catch (e) { toast(e instanceof Error ? e.message : 'Không ghi nhận được', 'error') }
        finally { setBusy(false) }
      }}>Xác nhận đã đối soát và hoàn tiền</button>
    </>}
  </section>
}
