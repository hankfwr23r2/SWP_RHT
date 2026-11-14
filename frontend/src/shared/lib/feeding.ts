// Thông tin cho ăn khách khai cho từng ngựa lúc đặt chuyến (giờ ăn quen, khẩu phần, có gửi kèm thức ăn không).
// Hộ tống đọc để cho ăn bữa chính ở trạm gần giờ ăn quen nhất. Cùng giới hạn độ dài với backend HorseFeedingPolicy.
import type { HorseFeeding } from '../types/booking'

export const FEEDING_USUAL_TIMES_MAX = 100
export const FEEDING_RATION_MAX = 500

// Bỏ khoảng trắng thừa; khách không khai gì thì trả undefined để không gửi lên và không hiển thị
export function cleanFeeding(feeding: HorseFeeding | undefined): HorseFeeding | undefined {
  if (!feeding) return undefined
  const usualTimes = feeding.usualTimes?.trim() || undefined
  const ration = feeding.ration?.trim() || undefined
  const ownFeed = feeding.ownFeed === true
  if (!usualTimes && !ration && !ownFeed) return undefined
  return { ...(usualTimes ? { usualTimes } : {}), ...(ration ? { ration } : {}), ownFeed }
}

// Các dòng hiển thị cho nhân viên: [nhãn, nội dung]
export function feedingLines(feeding: HorseFeeding | undefined): [label: string, text: string][] {
  const clean = cleanFeeding(feeding)
  if (!clean) return []
  const lines: [string, string][] = []
  if (clean.usualTimes) lines.push(['Giờ ăn quen', clean.usualTimes])
  if (clean.ration) lines.push(['Khẩu phần, ghi chú', clean.ration])
  if (clean.ownFeed) lines.push(['Thức ăn', 'Khách gửi kèm thức ăn riêng'])
  return lines
}
