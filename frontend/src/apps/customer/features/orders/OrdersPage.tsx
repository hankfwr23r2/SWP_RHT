import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import { OrderList } from './components/OrderList'
import s from './Orders.module.css'

// Đơn của tôi: các đơn chưa kết thúc, đơn cần khách làm nằm trên cùng
export default function OrdersPage() {
  const { t } = useTranslation()
  return (
    <div className="page"><div className="wrap">
      <header className={s.heading}>
        <div><h1>{t('myOrders.title')}</h1><p>{t('myOrders.subtitle')}</p></div>
        <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" aria-hidden="true" /> {t('common.bookNewTrip')}</Link>
      </header>
      <OrderList groups={['action', 'processing', 'moving']} emptyTitle={t('myOrders.empty')} emptyHint={t('myOrders.emptyHint')} />
    </div></div>
  )
}
