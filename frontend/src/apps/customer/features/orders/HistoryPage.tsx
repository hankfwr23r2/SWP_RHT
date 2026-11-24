import { useTranslation } from 'react-i18next'
import { OrderList } from './components/OrderList'
import s from './Orders.module.css'

// Lịch sử: đơn đã hoàn thành hoặc đã đóng
export default function HistoryPage() {
  const { t } = useTranslation()
  return (
    <div className="page"><div className="wrap">
      <header className={s.heading}><div><h1>{t('myOrders.historyTitle')}</h1><p>{t('myOrders.historySubtitle')}</p></div></header>
      <OrderList groups={['done', 'closed']} emptyTitle={t('myOrders.historyEmpty')} emptyHint={t('myOrders.historyEmptyHint')} />
    </div></div>
  )
}
