// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Card } from './Card'

afterEach(cleanup)

describe('Card', () => {
  it('có tiêu đề cấp 2, vùng thao tác và nội dung', () => {
    render(<Card title="Xe" actions={<button>Thêm xe</button>}><p>Danh sách xe</p></Card>)
    expect(screen.getByRole('heading', { level: 2, name: 'Xe' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Thêm xe' })).toBeTruthy()
    expect(screen.getByText('Danh sách xe')).toBeTruthy()
  })

  it('không có tiêu đề và thao tác thì không vẽ phần đầu', () => {
    const { container } = render(<Card><p>Nội dung</p></Card>)
    expect(container.querySelector('header')).toBeNull()
  })
})
