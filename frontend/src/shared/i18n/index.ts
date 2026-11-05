import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { vi } from './locales/vi.ts'
import { en } from './locales/en.ts'

const savedLng = typeof window !== 'undefined' ? localStorage.getItem('i18nextLng') ?? 'vi' : 'vi'
const initialLng = savedLng.startsWith('en') ? 'en' : 'vi'

void i18n
  .use(initReactI18next)
  .init({
    resources: {
      vi: { translation: vi },
      en: { translation: en },
    },
    lng: initialLng,
    fallbackLng: 'vi',
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n

