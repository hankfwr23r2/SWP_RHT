export const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024

export function documentUploadError(file: Pick<File, 'size'>): string | undefined {
  if (file.size > MAX_DOCUMENT_BYTES) return 'Tệp vượt quá 5 MB. Vui lòng chọn tệp nhỏ hơn trước khi lưu.'
  if (file.size === 0) return 'Tệp đang trống. Vui lòng chọn tệp khác.'
  return undefined
}
