import { expect, it, vi } from 'vitest'
import i18n from './index'
import { textBundle } from './staffTexts'

it('chỉ tải một lần, có cả tiếng Anh khi đổi ngôn ngữ, lỗi thì lần sau tải lại', async () => {
  const load = vi.fn().mockResolvedValue({ thuTai: { a: 'Một' } })
  const en = vi.fn().mockResolvedValue({ thuTai: { a: 'One' } })
  const texts = textBundle(load, en)
  await Promise.all([texts(), texts()])
  expect(load).toHaveBeenCalledTimes(1)
  expect(i18n.t('thuTai.a', { lng: 'vi' })).toBe('Một')
  await i18n.changeLanguage('en')
  expect(i18n.t('thuTai.a')).toBe('One')
  await i18n.changeLanguage('vi')

  const failing = textBundle(vi.fn().mockRejectedValueOnce(new Error('mạng')).mockResolvedValue({ thuLai: 'Lại' }), en)
  await expect(failing()).rejects.toThrow('mạng')
  await failing()
  expect(i18n.t('thuLai')).toBe('Lại')
})
