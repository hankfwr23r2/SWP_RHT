import { useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { customerHorseApi, type Horse, type HorseDocument, type HorseUpdate } from '@shared/api/horses'
import { MAX_DOCUMENT_BYTES } from '@shared/lib/documentUpload'
import { isIsoMicrochip, normalizeMicrochip } from '@shared/lib/microchip'
import { useToast } from '@shared/ui/toast'
import { currentHorseYear, MIN_BIRTH_YEAR } from './horseProfileDisplay'

export const HORSE_SEXES = ['STALLION', 'MARE', 'GELDING'] as const
export type HorseFormTab = 'info' | 'documents'
export type SelectedDocuments = Record<string, { file: File; error?: string; invalid?: boolean }>
type InfoErrors = Partial<Record<keyof HorseUpdate | 'microchip', string>>

export function useHorseForm(horse: Horse | undefined, initialDocuments: HorseDocument[], initialTab: HorseFormTab,
  onSaved: (complete: boolean) => void) {
  const { t } = useTranslation()
  const toast = useToast()
  const [profileId, setProfileId] = useState(horse?.id)
  const [tab, setTab] = useState(initialTab)
  const [values, setValues] = useState({
    name: horse?.name ?? '',
    microchip: horse?.microchip ?? '',
    horseTypeId: String(horse?.horseTypeId ?? ''),
    sex: horse?.sex ?? '',
    birthYear: String(horse?.birthYear ?? ''),
    color: horse?.color ?? '',
    identificationMarks: horse?.identificationMarks ?? '',
  })
  const [errors, setErrors] = useState<InfoErrors>({})
  const [documents, setDocuments] = useState(initialDocuments)
  const [selected, setSelected] = useState<SelectedDocuments>({})
  const [partial, setPartial] = useState(false)
  const [busy, setBusy] = useState(false)
  const saving = useRef(false)
  const savedValues = useRef<typeof values>(undefined)

  const set = (key: keyof typeof values, value: string) => {
    setValues(current => ({ ...current, [key]: value }))
    setErrors(current => ({ ...current, [key]: undefined }))
  }

  const selectFile = (code: string, file?: File) => {
    setSelected(current => {
      const next = { ...current }
      if (file) {
        const error = file.size === 0 ? t('horses.emptyFile')
          : file.size > MAX_DOCUMENT_BYTES ? t('horses.largeFile') : undefined
        next[code] = { file, error, invalid: !!error }
      } else {
        delete next[code]
      }
      return next
    })
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (saving.current) return
    const microchip = normalizeMicrochip(values.microchip)
    const sex = HORSE_SEXES.find(value => value === values.sex)
    const birthYear = Number(values.birthYear)
    const nextErrors: InfoErrors = {}
    if (!values.name.trim()) nextErrors.name = t('horses.required')
    if (!values.horseTypeId) nextErrors.horseTypeId = t('horses.required')
    if (!sex) nextErrors.sex = t('horses.required')
    if (profileId == null && !isIsoMicrochip(microchip)) nextErrors.microchip = t('horses.invalidChip')
    if (!values.color.trim()) nextErrors.color = t('horses.requiredColor')
    if (!Number.isInteger(birthYear) || birthYear < MIN_BIRTH_YEAR || birthYear > currentHorseYear()) {
      nextErrors.birthYear = t('horses.invalidBirthYear')
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length || !sex) {
      setTab('info')
      return
    }
    if (Object.values(selected).some(choice => choice.invalid)) {
      setTab('documents')
      return
    }

    saving.current = true
    setBusy(true)
    try {
      let id = profileId
      if (savedValues.current !== values || id == null) {
        const body: HorseUpdate = {
          name: values.name.trim(), sex, horseTypeId: Number(values.horseTypeId), birthYear,
          color: values.color.trim(), identificationMarks: values.identificationMarks.trim() || undefined,
        }
        const saved = id == null ? await customerHorseApi.create({ ...body, microchip }) : await customerHorseApi.update(id, body)
        id = saved.id ?? id
        if (id == null) throw new Error(t('horses.saveError'))
        setProfileId(id)
        savedValues.current = values
      }
      const failed: SelectedDocuments = {}
      const uploaded = new Map<string, HorseDocument>()
      for (const [code, { file }] of Object.entries(selected)) {
        try {
          uploaded.set(code, await customerHorseApi.uploadDocument(id, code, file))
        } catch (error) {
          const fields = error instanceof Error && 'fields' in error ? error.fields as Record<string, string> : {}
          failed[code] = { file, error: fields.file || fields.documentTypeCode || t('horses.documentSaveError') }
        }
      }
      setSelected(failed)
      setDocuments(current => current.map(document => uploaded.get(document.documentTypeCode ?? '') ?? document))
      const complete = Object.keys(failed).length === 0
      setPartial(!complete)
      if (!complete) setTab('documents')
      toast(t(complete ? 'horses.saved' : 'horses.partialSave'), complete ? 'success' : 'error')
      onSaved(complete)
    } catch (error) {
      if (error instanceof Error && 'fields' in error) {
        const fields = { ...error.fields as InfoErrors }
        if (error.message === 'Mã microchip đã được đăng ký.') fields.microchip = t('horses.duplicate')
        if (error.message === 'Nhập năm sinh hợp lệ.' || fields.birthYear === 'Nhập năm sinh hợp lệ.') {
          fields.birthYear = t('horses.invalidBirthYear')
        }
        if (fields.color === 'Nhập màu lông.') fields.color = t('horses.requiredColor')
        if (error.message === 'Loại ngựa không hợp lệ.') fields.horseTypeId = t('horses.invalidType')
        if (error.message.startsWith('Mã microchip phải ')) fields.microchip = t('horses.invalidChip')
        setErrors(fields)
      }
      setTab('info')
      toast(t('horses.saveError'), 'error')
    } finally {
      saving.current = false
      setBusy(false)
    }
  }

  return { profileId, tab, setTab, values, set, errors, documents, selected, selectFile, partial, busy, saving, submit }
}
