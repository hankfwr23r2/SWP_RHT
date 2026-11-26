// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import { i18nReady } from '@shared/i18n'
import { OrderRoute } from './OrderRoute'

beforeAll(() => i18nReady)
afterEach(cleanup)

describe('OrderRoute', () => {
  it('hiện nơi giao kèm nước với đơn xuất', () => {
    render(<OrderRoute routeName="TP.HCM – Cửa khẩu Mộc Bài (sang Campuchia)" bookingType="EXPORT"
      destinationProvinceName="Phnom Penh" destinationCountryName="Campuchia" />)
    expect(screen.getByText('Giao tại Phnom Penh, Campuchia')).toBeTruthy()
  })

  it('đơn trong nước chỉ hiện tỉnh, thiếu tuyến thì dùng chữ thay thế', () => {
    render(<OrderRoute fallback="Liên tỉnh" bookingType="INTERPROVINCE" destinationProvinceName="Thành phố Huế" destinationCountryName="Việt Nam" />)
    expect(screen.getByText('Liên tỉnh')).toBeTruthy()
    expect(screen.getByText('Giao tại Thành phố Huế')).toBeTruthy()
  })
})
