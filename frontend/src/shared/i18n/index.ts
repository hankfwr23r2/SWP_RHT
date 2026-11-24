import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { common } from './locales/vi/common'
import { customer } from './locales/vi/customer'

// Chữ dùng chung và của khách nằm ở đây; chữ của nhân viên ở staff.ts, chỉ app nội bộ tải.
// Tiếng Việt có sẵn trong gói, tiếng Anh chỉ tải khi người dùng chọn.
export const enParts: (() => Promise<object>)[] = [
  () => import('./locales/en/common').then(m => m.common),
  () => import('./locales/en/customer').then(m => m.customer),
]

const savedLng = typeof window !== 'undefined' ? localStorage.getItem('i18nextLng') ?? 'vi' : 'vi'
const initialLng = savedLng.startsWith('en') ? 'en' : 'vi'

// main.tsx chờ i18nReady trước khi vẽ trang
export const i18nReady = i18n
  .use({
    type: 'backend',
    init() {},
    read(lng: string, _ns: string, done: (error: unknown, data: object | null) => void) {
      if (!lng.startsWith('en')) { done(null, {}); return }
      // Chờ một nhịp để staff.ts kịp đăng ký phần của nhân viên
      Promise.resolve()
        .then(() => Promise.all(enParts.map(part => part())))
        .then(parts => done(null, Object.assign({}, ...parts) as object), error => done(error, null))
    },
  })
  .use(initReactI18next)
  .init({
    resources: {
      vi: { translation: { ...common, ...customer } },
    },
    partialBundledLanguages: true,
    lng: initialLng,
    fallbackLng: 'vi',
    interpolation: {
      escapeValue: false,
    },
  })

export default i18n
