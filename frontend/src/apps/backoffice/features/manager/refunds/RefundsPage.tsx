import { Input } from '@shared/ui/Input'
import { ListPanel } from '@shared/ui/ListPanel'
// Khoản hoàn: quản lý chuyển tiền ngoài hệ thống rồi ghi nhận mã giao dịch và chứng từ.
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { refundsApi, type Refund, type RefundStatus } from '@shared/api/refunds'
import { useLoad } from '@shared/hooks/useLoad'
import { invalidateStaffWork } from '@backoffice/shared/useStaffWork'
import { Button } from '@shared/ui/Button'
import { EmptyState } from '@shared/ui/EmptyState'
import { ErrorState } from '@shared/ui/ErrorState'
import { PageHeader } from '@shared/ui/PageHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Table, type TableColumn } from '@shared/ui/Table'
import { money, moment } from '@backoffice/shared/staffFormat'
import { MarkRefundedDialog } from './MarkRefundedDialog'
import s from '@backoffice/shared/StaffPanels.module.css'

const STATUSES: RefundStatus[] = ['PENDING', 'PAID']

export default function RefundsPage() {
  const { t } = useTranslation()
  const [params, setParams] = useSearchParams()
  const status = STATUSES.find(item => item === params.get('status')) ?? 'PENDING'
  const { data, loaded, error, reload } = useLoad(() => refundsApi.list(status), [status], { toastOnError: false })
  const [marking, setMarking] = useState<Refund | null>(null)

  const [query, setQuery] = useState('')
  const term = query.trim().toLowerCase()
  const linkedBooking = Number(params.get('bookingId'))
  const shown = data?.filter(row => (!linkedBooking || row.bookingId === linkedBooking)
    && (!term || [row.bookingCode, row.customerName, row.customerPhone].some(value => value?.toLowerCase().includes(term))))
  const columns: TableColumn<Refund>[] = [
    { key: 'order', header: t('mgr.refunds.order'), render: row => <Link to={`/manager/orders/${row.bookingId}`} className={s.link}>{row.bookingCode}</Link> },
    { key: 'customer', header: t('mgr.refunds.customer'), render: row => <>{row.customerName}<br />{row.customerPhone}</> },
    { key: 'of', header: t('mgr.refunds.of'), render: row => row.refundedPaymentType ? t(`myOrders.payments.type.${row.refundedPaymentType}`) : '—' },
    { key: 'amount', header: t('mgr.refunds.amount'), align: 'end', render: row => <span className={s.amount}>{money(row.amount, row.currencyCode)}</span> },
    status === 'PENDING'
      ? { key: 'action', header: t('mgr.refunds.action'), align: 'end', render: row => <Button size="sm" variant="primary" onClick={() => setMarking(row)}>{t('mgr.refunds.mark')}</Button> }
      : { key: 'paid', header: t('mgr.refunds.paidAt'), render: row => row.paidAt ? moment(row.paidAt) : '—' },
  ]
  const content = () => {
    if (error) return <ErrorState message={error.message} onRetry={reload} />
    if (!loaded || !data) return <Skeleton lines={5} />
    if (!shown?.length) return <EmptyState compact icon={<i className="fa-solid fa-rotate-left" />} title={t(term ? 'listPanel.noMatch' : 'mgr.refunds.empty')} description={t(term ? 'listPanel.trySearch' : 'mgr.refunds.emptyHint')} />
    return <Table caption={t('mgr.refunds.title')} columns={columns} rows={shown} rowKey={row => row.id ?? 0} />
  }
  return (
    <div className={s.page}>
      <PageHeader title={t('mgr.refunds.title')} description={t('mgr.refunds.subtitle')} />
      <ListPanel toolbar={<Input type="search" value={query} onChange={event => setQuery(event.target.value)}
          placeholder={t('listPanel.search')} aria-label={t('listPanel.search')} />} count={!error && loaded ? shown?.length : undefined} tabs={{ label: t('mgr.refunds.filter'), value: status,
        onChange: value => setParams({ status: value }, { replace: true }),
        items: STATUSES.map(value => ({ value, label: t(`mgr.refunds.statuses.${value}`) })) }}>
        {content()}
      </ListPanel>
      {marking && <MarkRefundedDialog refund={marking} onClose={() => setMarking(null)} onDone={() => { setMarking(null); invalidateStaffWork(); void reload() }} />}
    </div>
  )
}
