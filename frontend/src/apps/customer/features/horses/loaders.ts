// Dữ liệu các trang dùng hồ sơ ngựa của khách, router tải song song với file trang (xem shared/routing/pageData)
import { loadAll, type PageLoaderContext } from '@shared/routing/pageData'
import { useLoad } from '@shared/hooks/useLoad'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { backendHorseApi, customerHorseApi, HORSE_DOC_BACKEND_TYPE, type HorseCreate, type HorseUpdate } from '@shared/api/horses'
import type { HorseProfile } from '@shared/types/booking'
import { useToast } from '@shared/ui/toast'

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

type LegacyDocType = keyof typeof HORSE_DOC_BACKEND_TYPE
const legacyDocTypes = Object.keys(HORSE_DOC_BACKEND_TYPE) as LegacyDocType[]

export function useLegacyHorseDocuments(horse: HorseProfile, onSaved: (horse: HorseProfile) => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [saved, setSaved] = useState(horse.docs)
  const [edits, setEdits] = useState<Partial<Record<LegacyDocType, File | null>>>({})
  const [errors, setErrors] = useState<Partial<Record<LegacyDocType, string>>>({})
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const change = (type: LegacyDocType, file?: File) => {
    setEdits(current => ({ ...current, [type]: file ?? null }))
    setErrors(current => ({ ...current, [type]: undefined }))
  }

  const save = async () => {
    if (saving.current) return
    saving.current = true
    setBusy(true)
    const updated = { ...saved }
    const nextEdits = { ...edits }
    const nextErrors: typeof errors = {}
    try {
      for (const type of legacyDocTypes) {
        const file = edits[type]
        if (file === undefined) continue
        try {
          if (file) {
            const doc = await backendHorseApi.uploadDocument(Number(horse.id.replace(/\D/g, '')), HORSE_DOC_BACKEND_TYPE[type], file)
            updated[type] = {
              documentId: doc.id, fileName: file.name, fileUrl: doc.documentUrl ?? doc.fileUrl,
              uploadedAt: Date.now(), status: doc.status,
            }
          } else {
            const id = saved[type]?.documentId
            if (id != null) await backendHorseApi.deleteDocument(id)
            delete updated[type]
          }
          delete nextEdits[type]
        } catch {
          nextErrors[type] = t('horses.documentSaveError')
        }
      }
      setSaved(updated)
      setEdits(nextEdits)
      setErrors(nextErrors)
      if (Object.keys(nextErrors).length) toast(t('horses.documentSaveError'), 'error')
      else onSaved({ ...horse, docs: updated })
    } finally {
      setBusy(false)
      saving.current = false
    }
  }
  const documents = legacyDocTypes.map(type => ({
    type,
    document: edits[type] === undefined ? saved[type] : edits[type] ? { fileName: edits[type].name, fileUrl: undefined } : undefined,
    error: errors[type],
  }))
  return { documents, change, save, busy }
}

// Đặt chuyến bước 2 vẫn dùng hợp đồng cũ cho tới khi trang đó được chuyển đổi.
export async function horsesLoader({ session }: PageLoaderContext) {
  const [{ horsesApi }, { backendBookingApi }] = await Promise.all([import('@shared/services/horses'), import('@shared/api/bookings')])
  return loadAll({ horses: horsesApi.list(session.name), customerBookings: backendBookingApi.getCustomerBookings() })
}

// Đặt chuyến bước 3, 4: chỉ cần ngựa
export async function horseListLoader({ session }: PageLoaderContext) {
  const [{ horsesApi }, { serviceCatalogApi }] = await Promise.all([
    import('@shared/services/horses'),
    import('@shared/api/serviceCatalog'),
  ])
  return loadAll({
    horses: horsesApi.list(session.name),
    services: serviceCatalogApi.listActive(),
  })
}
