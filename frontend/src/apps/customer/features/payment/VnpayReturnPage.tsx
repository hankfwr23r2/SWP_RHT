// Trang VNPAY chuyển khách về sau khi thanh toán: gửi lại tham số cho backend kiểm tra chữ ký rồi báo kết quả.
// Trang công khai để không mất tham số vnp_* nếu phiên đăng nhập đã hết trong lúc khách ở cổng VNPAY.
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { paymentApi, vnpayParamsFrom, type VnpayResultDto } from '@shared/api/payments'
import { formatVND } from '@shared/lib/format'

export default function VnpayReturnPage() {
  const { t, i18n } = useTranslation()
  const { search } = useLocation()
  const params = useMemo(() => vnpayParamsFrom(search), [search])
  const [result, setResult] = useState<VnpayResultDto | null>(null)
  const [error, setError] = useState('')
  const sent = useRef(false)

  const paymentLabels: Record<string, string> = {
    DEPOSIT: t('vnpayReturn.deposit'),
    BALANCE: t('vnpayReturn.balance'),
    SETTLEMENT: t('vnpayReturn.settlement'),
  }

  useEffect(() => {
    if (sent.current || !params.vnp_TxnRef) return
    sent.current = true
    paymentApi.verifyVnpayReturn(params)
      .then(setResult)
      .catch(e => setError(e instanceof Error ? e.message : (i18n.language === 'en' ? 'Unable to verify payment result.' : 'Không kiểm tra được kết quả thanh toán.')))
  }, [params, i18n.language])

  const failure = !params.vnp_TxnRef ? (i18n.language === 'en' ? 'No transaction data from VNPAY.' : 'Không có thông tin giao dịch từ VNPAY.') : error
  const ok = result?.success === true
  const needsReview = result?.paymentStatus === 'NEEDS_REVIEW'

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
            <p><i className="fa-solid fa-spinner fa-spin" aria-hidden="true" /> {t('vnpayReturn.verifying')}</p>
          )}
          {(result || failure) && (
            <>
              <i className={`fa-solid ${ok ? 'fa-circle-check text-green' : needsReview ? 'fa-hourglass-half' : 'fa-circle-xmark text-red'}`} style={{ fontSize: 48 }} aria-hidden="true" />
              <h2 style={{ marginTop: 12 }}>{ok ? t('vnpayReturn.success') : needsReview ? (i18n.language === 'en' ? 'Payment received — under review' : 'Đã nhận tiền, cần đối soát') : t('vnpayReturn.failed')}</h2>
              {(result?.message || failure) && <p role="status">{result?.message || failure}</p>}
              {result?.amount !== undefined && (
                <div style={{ textAlign: 'left', margin: '16px 0', display: 'grid', gap: 6 }}>
                  {result.paymentType && <div><span className="text-muted">{t('vnpayReturn.item')} </span><b>{paymentLabels[result.paymentType] ?? result.paymentType}</b></div>}
                  <div><span className="text-muted">{t('vnpayReturn.amount')} </span><b>{formatVND(result.amount)}</b></div>
                  {result.bookingCode && <div><span className="text-muted">{t('vnpayReturn.orderCode')} </span><b>{result.bookingCode}</b></div>}
                  {result.gatewayTransactionNo && <div><span className="text-muted">{t('vnpayReturn.txnNo')} </span><b>{result.gatewayTransactionNo}</b></div>}
                </div>
              )}
              <div style={{ display: 'grid', gap: 8, marginTop: 16 }}>
                <Link className="btn btn-primary btn-full" to={targetOrder ? `/orders/${targetOrder}` : '/orders'}>
                  {ok || needsReview ? (targetOrder ? (i18n.language === 'en' ? 'View Order' : 'Xem đơn hàng') : t('vnpayReturn.backToOrders')) : (targetOrder ? (i18n.language === 'en' ? 'Return to Order' : 'Quay lại đơn hàng') : t('vnpayReturn.backToOrders'))}
                </Link>
                {!ok && targetOrder && (
                  <Link className="btn btn-ghost btn-full" to="/orders">
                    {t('vnpayReturn.backToOrders')}
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
