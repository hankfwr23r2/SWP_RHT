// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import i18n, { i18nReady } from '@shared/i18n'
import { AuthShell } from './AuthShell'

afterEach(async () => {
  cleanup()
  localStorage.removeItem('i18nextLng')
  await i18n.changeLanguage('vi')
})

it('places the language switch above the form and reflects the selection', async () => {
  await i18nReady
  render(<MemoryRouter><AuthShell title="Đăng nhập" subtitle=""><form aria-label="Đăng nhập" /></AuthShell></MemoryRouter>)
  const switcher = screen.getByRole('group', { name: 'Ngôn ngữ' })
  const form = screen.getByRole('form', { name: 'Đăng nhập' })
  expect(switcher.compareDocumentPosition(form) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  expect(within(switcher).getByRole('button', { name: 'VI' }).getAttribute('aria-pressed')).toBe('true')
  const english = within(switcher).getByRole('button', { name: 'EN' })
  fireEvent.click(english)
  await waitFor(() => expect(english.getAttribute('aria-pressed')).toBe('true'))
  expect(localStorage.getItem('i18nextLng')).toBe('en')
})
