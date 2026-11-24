// Trang VNPAY chuyển khách về sau khi thanh toán: gửi lại tham số cho backend kiểm tra chữ ký rồi báo kết quả.
// Trang công khai để không mất tham số vnp_* nếu phiên đăng nhập đã hết trong lúc khách ở cổng VNPAY.
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useTranslation } from 'react-i18next'
import { bookingPaymentsApi, vnpayParamsFrom, type PaymentResult } from '@shared/api/payments'
import { formatVND } from '@shared/lib/format'
import { Card } from '@shared/ui/Card'
import { Skeleton } from '@shared/ui/Skeleton'
import { StatusBadge } from '@shared/ui/StatusBadge'
import s from './VnpayReturn.module.css'

export default function VnpayReturnPage() {
  const { t } = useTranslation()
  const { search } = useLocation()
  const params = useMemo(() => vnpayParamsFrom(search), [search])
  const [result, setResult] = useState<PaymentResult>()
  const [error, setError] = useState(false)
  const sent = useRef(false)
  const missing = !params.vnp_TxnRef

  useEffect(() => {
    if (sent.current || missing) return
    sent.current = true
    bookingPaymentsApi.verifyReturn(params).then(setResult, () => setError(true))
  }, [params, missing])

  const outcome = result?.success ? 'success' : result?.needsReview ? 'review' : 'failed'
  const message = missing ? t('vnpayReturn.noData') : error ? t('vnpayReturn.checkError') : result?.message

  return (
    <div className="page"><div className={`wrap ${s.page}`}>
      <Card title={t('vnpayReturn.title')}>
        <div className={s.body} aria-live="polite">
          {!result && !message ? <Skeleton lines={3} label={t('vnpayReturn.verifying')} /> : (
            <>
              <StatusBadge tone={outcome === 'success' ? 'success' : outcome === 'review' ? 'warning' : 'danger'} label={t(`vnpayReturn.${outcome}`)} />
              {message && <p role="status">{message}</p>}
              {result?.amount != null && (
                <dl className={s.facts}>
                  {result.paymentType && <div><dt>{t('vnpayReturn.item')}</dt><dd>{t(`myOrders.payments.type.${result.paymentType}`)}</dd></div>}
                  <div><dt>{t('vnpayReturn.amount')}</dt><dd>{formatVND(result.amount)}</dd></div>
                  {result.bookingCode && <div><dt>{t('vnpayReturn.orderCode')}</dt><dd>{result.bookingCode}</dd></div>}
                  {result.transactionNumber && <div><dt>{t('vnpayReturn.txnNo')}</dt><dd>{result.transactionNumber}</dd></div>}
                </dl>
              )}
              <Link className="btn btn-primary btn-full" to="/orders">{t('vnpayReturn.backToOrders')}</Link>
            </>
          )}
        </div>
      </Card>
    </div></div>
  )
}
