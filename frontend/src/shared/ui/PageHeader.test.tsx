// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { PageHeader, PageTitleCode } from './PageHeader'

afterEach(cleanup)

describe('PageHeader', () => {
  it('giữ mã đơn trong một phần tử riêng của tiêu đề', () => {
    render(<PageHeader title={<>Đơn <PageTitleCode>DEV-DOMESTIC-001</PageTitleCode></>} />)
    const heading = screen.getByRole('heading', { name: 'Đơn DEV-DOMESTIC-001' })
    expect(heading.firstElementChild?.textContent).toBe('DEV-DOMESTIC-001')
  })

  it('tiêu đề là h1, có mô tả và nút thao tác', () => {
    render(<PageHeader title="Điều độ" description="Gán ngựa vào lượt xe" actions={<button>Tạo lượt xe</button>} />)
    expect(screen.getByRole('heading', { level: 1, name: 'Điều độ' })).toBeTruthy()
    expect(screen.getByText('Gán ngựa vào lượt xe')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Tạo lượt xe' })).toBeTruthy()
  })
})
