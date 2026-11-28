// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { EmptyState } from './EmptyState'

afterEach(cleanup)

describe('EmptyState', () => {
  it('nói rõ bước tiếp theo và có nút thao tác', () => {
    render(
      <EmptyState
        icon={<i className="fa-solid fa-horse" />}
        title="Chưa có hồ sơ ngựa"
        description="Bấm Thêm ngựa để tạo hồ sơ đầu tiên."
        action={<button>Thêm ngựa</button>}
      />,
    )
    expect(screen.getByRole('heading', { name: 'Chưa có hồ sơ ngựa' })).toBeTruthy()
    expect(screen.getByText('Bấm Thêm ngựa để tạo hồ sơ đầu tiên.')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Thêm ngựa' })).toBeTruthy()
  })
})
