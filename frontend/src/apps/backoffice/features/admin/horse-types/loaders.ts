// API danh mục hiện chỉ cấp quyền cho khách. Route admin vẫn mở được để hiển thị tình trạng quyền.
export async function loader() {
  return { errors: [] as string[] }
}
