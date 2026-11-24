// Dữ liệu các trang dùng hồ sơ ngựa của khách, router tải song song với file trang (xem shared/routing/pageData)
import { loadAll } from '@shared/routing/pageData'
import { useLoad } from '@shared/hooks/useLoad'
import { customerHorseApi, type HorseCreate, type HorseUpdate } from '@shared/api/horses'

export async function horseProfilesLoader() {
  return { errors: [], result: loadAll({ horses: customerHorseApi.list() }) }
}

export function useHorseFormData(id?: number) {
  return useLoad(async () => {
    const [types, horse] = await Promise.all([
      customerHorseApi.listTypes(),
      id == null ? undefined : customerHorseApi.getById(id),
    ])
    return { types, horse }
  }, [id], { toastOnError: false })
}

export function saveHorseProfile(id: number | undefined, body: HorseUpdate, microchip: HorseCreate['microchip']) {
  return id == null ? customerHorseApi.create({ ...body, microchip }) : customerHorseApi.update(id, body)
}
