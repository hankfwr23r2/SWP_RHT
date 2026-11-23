import { useEffect, useState } from 'react'

// Giờ hiện tại, cập nhật định kỳ. Dùng cho đồng hồ đếm ngược; tránh gọi Date.now() trực tiếp khi render.
export function useNow(intervalMs = 30_000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(id)
  }, [intervalMs])
  return now
}
