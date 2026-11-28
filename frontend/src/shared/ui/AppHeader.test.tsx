// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import { i18nReady } from '@shared/i18n'
import { AppHeader } from './AppHeader'

afterEach(cleanup)

it('opens navigation and account panels, and marks the current section', async () => {
  await i18nReady
  const logout = vi.fn()
  render(
    <MemoryRouter initialEntries={['/orders/1']}>
      <AppHeader homeHref="/portal" links={[[ '/orders', 'Đơn hàng', 'fa-inbox' ], [ '/terms', 'Điều khoản' ]]}
        isActive={(to, path) => path.startsWith(to)}
        userName="Nguyễn Văn Khách (mẫu)" profileHref="/profile" onLogout={logout} />
    </MemoryRouter>,
  )

  const menu = screen.getByRole('button', { name: 'Mở menu' })
  expect(menu.getAttribute('aria-expanded')).toBe('false')
  fireEvent.click(menu)
  expect(menu.getAttribute('aria-expanded')).toBe('true')
  expect(screen.getByRole('link', { name: 'Đơn hàng' }).getAttribute('aria-current')).toBe('page')
  expect(screen.getByRole('link', { name: 'Điều khoản' })).toBeTruthy()

  const account = screen.getByRole('button', { name: 'Tài khoản' })
  expect(account.textContent).toBe('VK')
  fireEvent.click(account)
  expect(menu.getAttribute('aria-expanded')).toBe('false')
  expect(account.getAttribute('aria-expanded')).toBe('true')
  expect(screen.getByRole('link', { name: 'Xem hồ sơ cá nhân' }).getAttribute('href')).toBe('/profile')
  fireEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }))
  expect(logout).toHaveBeenCalledOnce()
})
