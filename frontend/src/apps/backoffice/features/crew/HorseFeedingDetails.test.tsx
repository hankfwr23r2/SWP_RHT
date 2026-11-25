// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { HorseFeedingDetails } from './HorseFeedingDetails'

it('hiển thị chỉ dẫn, số bữa và bên cấp thức ăn của từng con', () => {
  render(<HorseFeedingDetails horse={{ dietNote: 'Cỏ khô', feedingsPerDay: 3, feedSuppliedByOwner: true }} />)
  expect(screen.getByText('Cỏ khô')).toBeTruthy()
  expect(screen.getByText('3 bữa/ngày')).toBeTruthy()
  expect(screen.getByText('Chủ ngựa cấp thức ăn')).toBeTruthy()
})

it('hiển thị rõ khi khách không dặn riêng', () => {
  render(<HorseFeedingDetails horse={{ dietNote: '  ', feedSuppliedByOwner: false }} />)
  expect(screen.getByText('Khách không dặn riêng')).toBeTruthy()
  expect(screen.getByText('Chưa có số bữa')).toBeTruthy()
  expect(screen.getByText('Đơn vị vận chuyển cấp thức ăn')).toBeTruthy()
})
