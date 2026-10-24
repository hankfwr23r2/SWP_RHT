// Trang VNPAY chuyển khách về sau khi thanh toán: gửi lại tham số cho backend kiểm tra chữ ký rồi báo kết quả.
// Trang công khai để không mất tham số vnp_* nếu phiên đăng nhập đã hết trong lúc khách ở cổng VNPAY.
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { paymentApi, vnpayParamsFrom, type VnpayResultDto } from '@shared/api/payments'
import { formatVND } from '@shared/lib/format'

const PAYMENT_LABEL: Record<string, string> = {
  BALANCE: 'Thanh toán 70% còn lại',
  SETTLEMENT: 'Thanh toán quyết toán',
}

export default function VnpayReturnPage() {
  const { search } = useLocation()
  const params = useMemo(() => vnpayParamsFrom(search), [search])
  const [result, setResult] = useState<VnpayResultDto | null>(null)
  const [error, setError] = useState('')
  const sent = useRef(false)

  useEffect(() => {
    if (sent.current || !params.vnp_TxnRef) return
    sent.current = true
    paymentApi.verifyVnpayReturn(params)
      .then(setResult)
      .catch(e => setError(e instanceof Error ? e.message : 'Không kiểm tra được kết quả thanh toán.'))
  }, [params])

  const failure = !params.vnp_TxnRef ? 'Không có thông tin giao dịch từ VNPAY.' : error
  const ok = result?.success === true

  const targetOrder = useMemo(() => {
    if (result?.bookingCode) return result.bookingCode
    const txn = params.vnp_TxnRef
    if (txn && txn.length >= 7) {
      const numPart = txn.slice(3, 7)
      const id = parseInt(numPart, 10)
      if (!isNaN(id)) return `BK-${String(id).padStart(3, '0')}`
    }
    return null
  }, [result?.bookingCode, params.vnp_TxnRef])

  return (
    <div className="page">
      <div className="wrap" style={{ maxWidth: 560 }}>
        <div className="card" style={{ textAlign: 'center' }} aria-live="polite">
          {!result && !failure && (
            <p><i className="fa-solid fa-spinner fa-spin" aria-hidden="true" /> Đang kiểm tra kết quả thanh toán…</p>
          )}
          {(result || failure) && (
            <>
              <i className={`fa-solid ${ok ? 'fa-circle-check text-green' : 'fa-circle-xmark text-red'}`} style={{ fontSize: 48 }} aria-hidden="true" />
              <h2 style={{ marginTop: 12 }}>{ok ? 'Thanh toán thành công' : 'Thanh toán chưa thành công'}</h2>
              <p className="text-muted">{failure || result?.message}</p>
              {result?.amount !== undefined && (
                <div style={{ textAlign: 'left', margin: '16px 0', display: 'grid', gap: 6 }}>
                  {result.paymentType && <div><span className="text-muted">Khoản thanh toán: </span><b>{result.paymentType === 'DEPOSIT' ? 'Đặt cọc' : PAYMENT_LABEL[result.paymentType] ?? result.paymentType}</b></div>}
                  <div><span className="text-muted">Số tiền: </span><b>{formatVND(result.amount)}</b></div>
                  {result.bookingCode && <div><span className="text-muted">Đơn hàng: </span><b>{result.bookingCode}</b></div>}
                  {result.gatewayTransactionNo && <div><span className="text-muted">Mã giao dịch VNPAY: </span><b>{result.gatewayTransactionNo}</b></div>}
                </div>
              )}
              <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
                <Link className="btn btn-primary btn-full" to={targetOrder ? `/orders/${targetOrder}` : '/orders'}>
                  {ok ? (targetOrder ? 'Xem đơn hàng' : 'Về danh sách đơn') : (targetOrder ? 'Quay lại đơn hàng để thanh toán lại' : 'Về danh sách đơn')}
                </Link>
                {!ok && targetOrder && (
                  <Link className="btn btn-ghost btn-full" to="/orders">
                    Về danh sách đơn
                  </Link>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
