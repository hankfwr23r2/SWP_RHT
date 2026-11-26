import { describe, expect, it } from 'vitest'
import { vi } from './locales/vi'
import { en } from './locales/en'

// Mọi chữ hiển thị có đủ cả vi và en (AGENTS.md mục 8.4). Mỗi ngôn ngữ chia thành các file theo khu vực rồi ghép lại.
const areas = import.meta.glob<Record<string, Record<string, unknown>>>(['./locales/*/*.ts', '!./locales/*/index.ts'], { eager: true })

const keysOf = (value: unknown, prefix = ''): string[] =>
  typeof value === 'object' && value !== null
    ? Object.entries(value).flatMap(([key, child]) => keysOf(child, prefix ? `${prefix}.${key}` : key))
    : [prefix]

// { 'vi/common': ['ui', 'common', ...], ... }: các nhóm khóa ngoài cùng của từng file
const groupsByFile = Object.fromEntries(Object.entries(areas).map(([path, module]) => [
  path.replace('./locales/', '').replace('.ts', ''),
  Object.values(module).flatMap(exported => Object.keys(exported)),
]))
const filesOf = (lang: string) => Object.keys(groupsByFile).filter(file => file.startsWith(`${lang}/`)).map(file => file.slice(lang.length + 1)).sort()

describe('chữ hiển thị vi và en', () => {
  it('hai ngôn ngữ có cùng các khóa', () => {
    const viKeys = new Set(keysOf(vi))
    const enKeys = new Set(keysOf(en))
    expect([...viKeys].filter(key => !enKeys.has(key)), 'có ở vi, thiếu ở en').toEqual([])
    expect([...enKeys].filter(key => !viKeys.has(key)), 'có ở en, thiếu ở vi').toEqual([])
  })

  it('mỗi file khu vực có ở cả hai ngôn ngữ và chứa cùng các nhóm khóa', () => {
    expect(filesOf('en')).toEqual(filesOf('vi'))
    for (const file of filesOf('vi')) expect(groupsByFile[`en/${file}`], file).toEqual(groupsByFile[`vi/${file}`])
  })

  it('không nhóm khóa nào nằm ở hai file (khi ghép, file sau sẽ đè file trước)', () => {
    for (const [lang, merged] of [['vi', vi], ['en', en]] as const) {
      const groups = filesOf(lang).flatMap(file => groupsByFile[`${lang}/${file}`])
      expect(groups.length, lang).toBe(new Set(groups).size)
      expect(groups.length, lang).toBe(Object.keys(merged).length)
    }
  })
})
