import type { CustodyRow } from '@shared/api/custody'

const plain = (value: string) => value.toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '').replace(/đ/g, 'd')

// Tìm theo tên ngựa, mã đơn, nơi ở, người giữ, mã lượt xe; không phân biệt dấu
export function matchesCustody(row: CustodyRow, query: string) {
  const text = plain([row.horseName, row.bookingCode, row.placeName, row.holderName, row.tripCode].join(' '))
  return plain(query.trim()).split(/\s+/).every(word => text.includes(word))
}
