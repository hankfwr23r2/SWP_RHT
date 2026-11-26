import { useTranslation } from 'react-i18next'
import s from './OrderRoute.module.css'

interface Props {
  routeName?: string
  fallback?: string
  bookingType?: string
  destinationProvinceName?: string
  destinationCountryName?: string
}

// Tuyến của đơn kèm nơi giao thật, vì tuyến quốc tế chỉ chạy tới cửa khẩu
export function OrderRoute({ routeName, fallback, bookingType, destinationProvinceName, destinationCountryName }: Props) {
  const { t } = useTranslation()
  const place = [destinationProvinceName, bookingType === 'EXPORT' ? destinationCountryName : undefined].filter(Boolean).join(', ')
  return <div>
    <span>{routeName || fallback || '—'}</span>
    {place && <p className={s.destination}>{t('listPanel.destination', { place })}</p>}
  </div>
}
