import type { TFunction } from 'i18next'
import type { HorseDocument } from '@shared/api/horses'

const year = new Intl.DateTimeFormat('en', { year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' })

export const MIN_BIRTH_YEAR = 1990
export const currentHorseYear = () => Number(year.format(new Date()))

export function horseDocumentName(t: TFunction, document: HorseDocument) {
  return t(`horses.documentLabels.${document.documentTypeCode}`, {
    defaultValue: document.documentTypeName ?? document.documentTypeCode ?? '',
  })
}
