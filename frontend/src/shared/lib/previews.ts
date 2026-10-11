// Kho xem trước ảnh trong phiên: hệ thống chưa có kho tệp (chỉ lưu tên tệp), nên nhớ ảnh vừa chọn theo tên để hiện thumbnail và popup.
// Tải lại trang hoặc ảnh mẫu từ dữ liệu có sẵn thì không có ảnh thật, giao diện hiện ô thay thế.
const previews = new Map<string, string>()

export function rememberPreview(name: string, file: Blob) {
  if (!file.type.startsWith('image/')) return
  const old = previews.get(name)
  if (old) URL.revokeObjectURL(old)
  previews.set(name, URL.createObjectURL(file))
}
export const previewOf = (name?: string) => (name ? previews.get(name) : undefined)
