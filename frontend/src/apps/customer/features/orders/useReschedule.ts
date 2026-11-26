import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { bookingOrdersApi, type BookingReschedule } from '@shared/api/bookingOrders'
import { useLoad } from '@shared/hooks/useLoad'
import { dayAfter, todayIso } from './dates'
import type { OrderData } from './loaders'
import { useOrderAction } from './useOrderAction'

export function useReschedule(data: OrderData, reschedule?: (body: BookingReschedule) => Promise<unknown>) {
  const { t } = useTranslation()
  const { run, busy, error } = useOrderAction()
  const [today] = useState(() => todayIso())
  const booking = data.detail?.booking
  const showMerge = booking?.status === 'SCHEDULE_CHANGE_REQUIRED'
  const merge = useLoad(() => showMerge ? bookingOrdersApi.mergeSuggestion(data.id) : Promise.resolve(null),
    [data.id, showMerge], { toastOnError: false })

  const suggestions = useLoad(() => {
    const pickup = data.detail?.addresses?.find(address => address.addressType === 'PICKUP')
    const delivery = data.detail?.addresses?.find(address => address.addressType === 'DELIVERY')
    const seats = (data.detail?.horses ?? []).filter(horse => horse.status === 'ACTIVE')
      .reduce((sum, horse) => sum + (data.stallTypes?.find(stall => stall.code === horse.stallTypeCode)?.seats ?? 1), 0)
    return bookingOrdersApi.suggestions({
      bookingType: booking!.bookingType!, fromDate: dayAfter(today), stalls: Math.max(1, seats),
      pickupProvinceCode: pickup?.provinceCode ?? '', deliveryProvinceCode: delivery?.provinceCode ?? '',
      pickupLatitude: pickup?.latitude, pickupLongitude: pickup?.longitude,
      deliveryLatitude: delivery?.latitude, deliveryLongitude: delivery?.longitude,
    })
  }, [data.id], { toastOnError: false })

  const done = t('myOrders.reschedule.done')
  const submit = (body: BookingReschedule) => run(
    () => reschedule ? reschedule(body) : bookingOrdersApi.reschedule(data.id, body), done)

  const accept = async () => {
    const choice = merge.data
    if (!choice) return
    const body = { departureId: choice.departureId, requestedPickupDate: choice.requestedPickupDate }
    if (await run(() => bookingOrdersApi.reschedule(data.id, body), done)) {
      merge.updateData(() => null)
    } else {
      void merge.reload()
    }
  }

  return { today, merge, suggestions, submit, accept, busy, error }
}
