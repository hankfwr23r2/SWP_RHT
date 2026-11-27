// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { rememberPreview } from '@shared/lib/previews'
import { FilePreviewButton } from './FilePreviewButton'

vi.mock('@shared/lib/previews', () => ({
  rememberPreview: vi.fn(),
  previewOf: () => 'blob:http://localhost/preview',
}))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

it('opens an extensionless uploaded PDF URL in the in-app viewer', () => {
  const fileUrl = 'https://files.example.test/document?id=42'
  render(<FilePreviewButton fileName="passport.pdf" fileUrl={fileUrl} label="Xem" />)

  fireEvent.click(screen.getByRole('button', { name: 'Xem passport.pdf' }))

  expect(screen.getByTitle('passport.pdf').getAttribute('src')).toBe(fileUrl)
})

it('registers a selected PDF file and opens its local preview', () => {
  const file = new File(['pdf'], 'selected.pdf', { type: 'application/pdf' })
  render(<FilePreviewButton fileName={file.name} file={file} label="Xem" />)

  fireEvent.click(screen.getByRole('button', { name: 'Xem selected.pdf' }))

  expect(rememberPreview).toHaveBeenCalledWith(file.name, file)
  expect(screen.getByTitle(file.name).getAttribute('src')).toBe('blob:http://localhost/preview')
})
