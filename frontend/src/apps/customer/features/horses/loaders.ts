// Dữ liệu các trang dùng hồ sơ ngựa của khách, router tải song song với file trang (xem shared/routing/pageData)
import { loadAll } from '@shared/routing/pageData'
import { useLoad } from '@shared/hooks/useLoad'
import { customerHorseApi } from '@shared/api/horses'

export async function horseProfilesLoader() {
  return { errors: [], result: loadAll({ horses: customerHorseApi.list() }) }
}

export function useHorseFormData(id?: number) {
  return useLoad(async () => {
    const [types, horse, documents] = await Promise.all([
      customerHorseApi.listTypes(),
      id == null ? undefined : customerHorseApi.getById(id),
      id != null ? customerHorseApi.listDocuments(id) : customerHorseApi.listDocumentTypes().then(catalog =>
        catalog.map(type => ({ documentTypeCode: type.code, documentTypeName: type.name, canUpload: true }))),
    ])
    return { types, horse, documents }
  }, [id], { toastOnError: false })
}
