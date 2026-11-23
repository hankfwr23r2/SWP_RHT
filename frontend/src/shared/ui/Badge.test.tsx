// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Badge } from './Badge'
import { StatusBadge, type StatusTone } from './StatusBadge'

afterEach(cleanup)

describe('Badge', () => {
  it('hiện chữ, biểu tượng bị ẩn với trình đọc màn hình', () => {
    render(<Badge tone="primary" icon={<i className="fa-solid fa-star" />}>Mới</Badge>)
    const badge = screen.getByText('Mới')
    expect(badge.querySelector('[aria-hidden="true"] i')).not.toBeNull()
  })
})

describe('StatusBadge', () => {
  it.each<StatusTone>(['success', 'warning', 'danger', 'info', 'neutral'])(
    'sắc thái %s luôn có chữ và biểu tượng, không chỉ dựa vào màu',
    tone => {
      render(<StatusBadge tone={tone} label="Trạng thái thử" />)
      const badge = screen.getByText('Trạng thái thử')
      expect(badge.textContent).toBe('Trạng thái thử')
      expect(badge.querySelector('i.fa-solid')).not.toBeNull()
    },
  )

  it('mỗi sắc thái một biểu tượng riêng', () => {
    const icons = (['success', 'warning', 'danger', 'info', 'neutral'] as StatusTone[]).map(tone => {
      const { container, unmount } = render(<StatusBadge tone={tone} label={tone} />)
      const icon = container.querySelector('i')?.className
      unmount()
      return icon
    })
    expect(new Set(icons).size).toBe(5)
  })
})
