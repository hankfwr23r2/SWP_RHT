import { useTranslation } from 'react-i18next'
import { formatVND } from '@shared/lib/format'
import { Skeleton } from '@shared/ui/Skeleton'
import { useBookingDraft } from '../draft'
import { findAddress, useBookingData, type BookingData } from '../loaders'
import { showDate, showDateTime } from '../model'
import { usePricePreview } from '../usePricePreview'
import s from './BookingSummary.module.css'

type Preview = ReturnType<typeof usePricePreview>
type Line = NonNullable<NonNullable<Preview['price']>['lines']>[number]
type Group = 'transport' | 'required' | 'chosen'

const TRANSPORT_LAYERS = ['LINEHAUL', 'PICKUP', 'DELIVERY', 'DIRECT', 'FUEL', 'STALL']

// Chia dòng giá thành ba nhóm; dịch vụ tuyến bắt buộc mà khách không tự chọn thì nằm ở nhóm bắt buộc
function groupLines(lines: Line[], data: BookingData, chosenIds: Set<number>) {
  const groups: Record<Group, { name: string; line: Line }[]> = { transport: [], required: [], chosen: [] }
  for (const line of lines) {
    const service = data.services.find(item => item.code === line.serviceCode)
    const stall = data.stallTypes.find(item => item.code === line.stallTypeCode)
    const group: Group = service?.id != null && chosenIds.has(service.id) ? 'chosen'
      : TRANSPORT_LAYERS.includes(service?.layer ?? '') ? 'transport' : 'required'
    groups[group].push({ name: [service?.name ?? line.serviceCode, stall?.name].filter(Boolean).join(' · '), line })
  }
  return groups
}

function PriceLines({ preview }: { preview: Preview }) {
  const { t } = useTranslation()
  const data = useBookingData()
  const { draft } = useBookingDraft()
  if (!preview.ready) return <p className={s.hint}>{t('bookingForm.price.waiting')}</p>
  if (preview.error != null) return <p className={s.error} role="alert">{preview.error || t('bookingForm.price.error')}</p>
  if (!preview.price) return <Skeleton lines={4} />
  const groups = groupLines(preview.price.lines ?? [], data,
    new Set(draft.horseIds.flatMap(id => draft.horses[id]?.addOnServiceIds ?? [])))
  const depositPercent = preview.price.depositRate == null ? null : Math.round(preview.price.depositRate * 100)
  return (
    <div className={s.lines} aria-busy={preview.loading}>
      {(['transport', 'required', 'chosen'] as const).map(group => groups[group].length > 0 && (
        <section key={group}>
          <h3>{t(`bookingForm.price.${group}`)}</h3>
          <dl>
            {groups[group].map(({ name, line }, index) => (
              <div key={`${line.priceItemId}-${index}`}>
                <dt>{name}</dt>
                <dd>{line.amount ? formatVND(line.amount) : t('bookingForm.price.included')}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
      {!!preview.price.deliveryEstimates?.length && (
        <section>
          <h3>{t('bookingForm.price.deliveryEstimates')}</h3>
          <dl>
            {preview.price.deliveryEstimates.map(place => place.arrivalAt && (
              <div key={place.customerAddressId}>
                <dt>{findAddress(data, place.customerAddressId ?? null)?.label}</dt>
                <dd>{showDateTime(place.arrivalAt)}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}
      <p className={s.hint}>{t('bookingForm.price.note')}{depositPercent != null && ` ${t('bookingForm.price.deposit', { rate: depositPercent })}`}</p>
    </div>
  )
}

export function BookingSummary() {
  const { t } = useTranslation()
  const { draft } = useBookingDraft()
  const data = useBookingData()
  const preview = usePricePreview(draft)
  const pickup = findAddress(data, draft.pickupAddressId)
  const delivery = findAddress(data, draft.deliveryAddressId)
  const total = preview.price?.total != null && preview.error == null ? formatVND(preview.price.total) : '—'

  return (
    <aside className={s.aside} aria-label={t('bookingForm.summary.title')}>
      <div className={s.card}>
        <h2>{t('bookingForm.summary.title')}</h2>
        <dl className={s.facts}>
          <div><dt>{t('bookingForm.summary.pickup')}</dt><dd>{pickup?.label ?? '—'}</dd></div>
          <div><dt>{t('bookingForm.summary.delivery')}</dt><dd>{delivery?.label ?? '—'}</dd></div>
          {draft.departure?.departureDate && (
            <div><dt>{t('bookingForm.summary.departure')}</dt><dd>{showDate(draft.departure.departureDate)}</dd></div>
          )}
          <div><dt>{t('bookingForm.summary.pickupDate')}</dt><dd>{draft.pickupDate ? showDate(draft.pickupDate) : '—'}</dd></div>
          <div><dt>{t('bookingForm.summary.horses')}</dt><dd>{draft.horseIds.length || '—'}</dd></div>
        </dl>
        <div className={s.desktopPrice}>
          <PriceLines preview={preview} />
          <p className={s.total}><span>{t('bookingForm.price.total')}</span><strong>{total}</strong></p>
        </div>
      </div>
      <details className={s.bar}>
        <summary><span>{t('bookingForm.price.total')}</span><strong>{total}</strong><i className="fa-solid fa-chevron-up" aria-hidden="true" /></summary>
        <PriceLines preview={preview} />
      </details>
    </aside>
  )
}
