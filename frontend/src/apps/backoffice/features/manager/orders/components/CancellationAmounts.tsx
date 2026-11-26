import { useTranslation } from 'react-i18next'
import type { BookingCancellation } from '@shared/api/bookingOrders'
import { money } from '@backoffice/shared/staffFormat'
import s from '@backoffice/shared/StaffPanels.module.css'

export function CancellationAmounts({ amounts }: { amounts: BookingCancellation }) {
  const { t } = useTranslation()
  const shown = (['refundable', 'reviewable', 'forfeited'] as const).filter(key => amounts[key])
  if (!shown.length) return <p><strong>{t('mgr.cancel.free')}</strong></p>
  return (
    <dl className={s.facts}>
      {shown.map(key => <div key={key}><dt>{t(`myOrders.cancel.${key}`)}</dt><dd><strong>{money(amounts[key], amounts.currencyCode)}</strong></dd></div>)}
    </dl>
  )
}
