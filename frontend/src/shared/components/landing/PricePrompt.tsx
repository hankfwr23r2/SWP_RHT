// Dẫn khách tới luồng đặt chuyến để xem giá tạm tính do hệ thống tính; trang chủ không tự tính giá
import { Link } from 'react-router'
import { useTranslation } from 'react-i18next'
import s from './PricePrompt.module.css'

const INPUTS = [
  { key: 'route', icon: 'fa-location-dot' },
  { key: 'horses', icon: 'fa-horse-head' },
  { key: 'services', icon: 'fa-kit-medical' },
]

export function PricePrompt() {
  const { t } = useTranslation()
  return (
    <div className={s.price}>
      <div>
        <h2>{t('home.price.title')}</h2>
        <p>{t('home.price.text')}</p>
        <ul>
          {INPUTS.map(({ key, icon }) => <li key={key}><i className={`fa-solid ${icon}`} aria-hidden="true" />{t(`home.price.${key}`)}</li>)}
        </ul>
      </div>
      <Link to="/booking/route" className="btn btn-solid btn-lg">{t('home.price.cta')}</Link>
    </div>
  )
}
