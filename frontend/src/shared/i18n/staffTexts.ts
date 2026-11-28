// Chữ của từng nhóm màn nhân viên, chỉ tải khi mở màn đó để gói tải đầu nhỏ.
import i18n, { enParts } from './index'

type Load = () => Promise<object>

// Tiếng Việt luôn tải (chữ dự phòng); tiếng Anh tải ngay nếu đang dùng, không thì tải cùng lúc người dùng đổi ngôn ngữ
export function textBundle(vi: Load, en: Load): () => Promise<void> {
  let loading: Promise<void> | undefined
  return () => loading ??= (async () => {
    const english = i18n.hasResourceBundle('en', 'translation') || i18n.language.startsWith('en')
    const [viText, enText] = await Promise.all([vi(), english ? en() : undefined])
    i18n.addResourceBundle('vi', 'translation', viText, true)
    if (enText) i18n.addResourceBundle('en', 'translation', enText, true)
    enParts.push(en)
  })().catch(error => { loading = undefined; throw error })
}

export const managerTexts = textBundle(
  () => import('./locales/vi/staffManager').then(m => m.staffManager),
  () => import('./locales/en/staffManager').then(m => m.staffManager),
)

export const dispatchTexts = textBundle(
  () => import('./locales/vi/staffDispatch').then(m => m.staffDispatch),
  () => import('./locales/en/staffDispatch').then(m => m.staffDispatch),
)

export const reviewTexts = textBundle(
  () => import('./locales/vi/staffReview').then(m => m.staffReview),
  () => import('./locales/en/staffReview').then(m => m.staffReview),
)
