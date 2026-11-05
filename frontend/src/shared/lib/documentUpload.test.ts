import { describe, expect, it } from 'vitest'
import { documentUploadError, MAX_DOCUMENT_BYTES } from './documentUpload'

describe('document upload size validation', () => {
  it('accepts a file at the 5 MB boundary', () => {
    expect(documentUploadError({ size: MAX_DOCUMENT_BYTES })).toBeUndefined()
  })
  it('blocks a file one byte over the limit', () => {
    expect(documentUploadError({ size: MAX_DOCUMENT_BYTES + 1 })).toContain('5 MB')
  })
  it('rejects empty files', () => {
    expect(documentUploadError({ size: 0 })).toContain('trống')
  })
})
