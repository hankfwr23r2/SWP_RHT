/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// Đọc thẳng tokens.css: đổi màu mà làm chữ tụt dưới 4.5:1 thì test đỏ (AGENTS.md mục 8.7).
// Vitest không nạp nội dung file CSS khi import, nên đọc bằng fs.
const css = readFileSync(new URL('./tokens.css', import.meta.url), 'utf8')
const tokens: Record<string, string> = Object.fromEntries(
  [...css.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map(([, name, value]) => [name, value.trim()]),
)

function hex(name: string): string {
  const value = tokens[name]
  if (!value) throw new Error(`Thiếu token ${name}`)
  const reference = value.match(/^var\((--[\w-]+)\)$/)
  return reference ? hex(reference[1]) : value
}

function luminance(color: string): number {
  const [r, g, b] = [1, 3, 5].map(i => parseInt(color.slice(i, i + 2), 16) / 255)
    .map(c => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(foreground: string, background: string): number {
  const [light, dark] = [luminance(hex(foreground)), luminance(hex(background))].sort((a, b) => b - a)
  return (light + 0.05) / (dark + 0.05)
}

// Các cặp chữ/nền mà component trong shared/ui dùng
const TEXT_PAIRS: [string, string][] = [
  ['--color-text', '--color-surface'],
  ['--color-text', '--color-bg'],
  ['--color-heading', '--color-surface'],
  ['--color-muted', '--color-surface'],
  ['--color-muted', '--color-bg'],
  ['--color-on-primary', '--color-primary'],
  ['--color-on-primary', '--color-primary-hover'],
  ['--color-primary', '--color-surface'],
  ['--color-primary', '--color-primary-soft'],
  ['--color-success-strong', '--color-success-soft'],
  ['--color-warning-strong', '--color-warning-soft'],
  ['--color-danger-strong', '--color-danger-soft'],
  ['--color-info-strong', '--color-info-soft'],
  ['--color-neutral-strong', '--color-neutral-soft'],
  ['--color-on-primary', '--color-danger-strong'],
]

describe('design token', () => {
  it.each(TEXT_PAIRS)('chữ %s trên nền %s đạt tương phản 4.5:1', (foreground, background) => {
    expect(contrast(foreground, background)).toBeGreaterThanOrEqual(4.5)
  })

  it('bí danh cũ trỏ đúng giá trị trước đây, để trang cũ không đổi màu', () => {
    expect(hex('--orange')).toBe('#ea580c')
    expect(hex('--orange-dark')).toBe('#c2410c')
    expect(hex('--navy')).toBe('#0f172a')
    expect(hex('--muted')).toBe('#64748b')
    expect(hex('--line')).toBe('#e2e8f0')
    expect(hex('--bg-page')).toBe('#f8fafc')
    expect(hex('--green')).toBe('#059669')
    expect(hex('--red')).toBe('#dc2626')
    expect(tokens['--radius-md']).toBe('12px')
  })

  it('khoảng cách theo thang 4px', () => {
    for (const [name, value] of Object.entries(tokens).filter(([name]) => name.startsWith('--space-'))) {
      expect(Number(value.replace('px', '')) % 4, name).toBe(0)
    }
  })
})
