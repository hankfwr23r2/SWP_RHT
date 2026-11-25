import { expect, it } from 'vitest'
import i18n from './index'
import './staff'
import { vi } from './locales/vi'
import { dispatchTexts, managerTexts, reviewTexts } from './staffTexts'

// Thêm file chữ mới mà quên nạp vào app thì màn hình hiện mã kỹ thuật
it('app nội bộ nạp đủ mọi nhóm chữ', async () => {
  await Promise.all([dispatchTexts(), managerTexts(), reviewTexts()])
  const loaded = i18n.getResourceBundle('vi', 'translation') as Record<string, unknown>
  expect(Object.keys(vi).filter(key => !(key in loaded))).toEqual([])
})
