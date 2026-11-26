import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { dispatchApi, type LinehaulTruck } from '@shared/api/dispatch'
import { useLoad } from '@shared/hooks/useLoad'
import { useToast } from '@shared/ui/toast'

export const truckKey = (truck: LinehaulTruck) => `${truck.allotmentId}-${truck.number}`

export function useLinehaulSuggestion(departureId: number, onCreated: (tripId: number) => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [choices, setChoices] = useState<Record<string, string>>({})
  const suggestion = useLoad(() => dispatchApi.linehaulSuggestion(departureId), [departureId], { toastOnError: false })
  const trucks = suggestion.data?.trucks ?? []
  const selected = trucks.map(truck => Number(choices[truckKey(truck)]))
  const ready = trucks.length > 0 && !suggestion.error && suggestion.data?.missingSeats === 0
    && selected.every((id, index) => trucks[index].vehicles?.some(vehicle => vehicle.id === id))
    && new Set(selected).size === selected.length

  const apply = async () => {
    try {
      const ids = await dispatchApi.applyLinehaul(departureId, { trucks: trucks.map((truck, index) => ({
        allotmentId: truck.allotmentId!,
        number: truck.number!,
        vehicleId: selected[index],
        bookingHorseIds: (truck.horses ?? []).map(horse => horse.bookingHorseId!),
      })) })
      toast(t('linehaul.created', { count: ids.length }), 'success')
      onCreated(ids[0])
    } catch (error) {
      toast(error instanceof Error ? error.message : t('linehaul.failed'), 'error')
      throw error
    }
  }

  return { ...suggestion, trucks, choices, ready, apply, choose: (key: string, value: string) =>
    setChoices(current => ({ ...current, [key]: value })) }
}
