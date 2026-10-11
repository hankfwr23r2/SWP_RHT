// Mạng lưới vận chuyển: 3 nước, điểm nhận/giao, cửa khẩu, trạm nghỉ. Khớp docs/PRD.md mục 1.

export type CountryCode = 'VN' | 'LA' | 'KH'

export const COUNTRIES: Record<CountryCode, { name: string; meta: string }> = {
  VN: { name: 'Việt Nam', meta: 'Nội địa & xuất phát' },
  LA: { name: 'Lào', meta: 'Qua 3 cửa khẩu' },
  KH: { name: 'Campuchia', meta: 'Qua 2 cửa khẩu' },
}

export interface GeoPoint { lat: number; lng: number }

// Điểm dùng cho tra cước ở trang chủ (tọa độ để ước tính km)
export interface Place extends GeoPoint { id: string; name: string; country: CountryCode }
export const PLACES: Place[] = [
  { id: 'hn', name: 'Hà Nội', country: 'VN', lat: 21.03, lng: 105.85 },
  { id: 'dn', name: 'Đà Nẵng', country: 'VN', lat: 16.05, lng: 108.2 },
  { id: 'qn', name: 'Quy Nhơn (Bình Định)', country: 'VN', lat: 13.78, lng: 109.22 },
  { id: 'hcm', name: 'TP. Hồ Chí Minh', country: 'VN', lat: 10.77, lng: 106.66 },
  { id: 'dni', name: 'Đồng Nai', country: 'VN', lat: 10.78, lng: 106.95 },
  { id: 'bd', name: 'Bình Dương', country: 'VN', lat: 11.0, lng: 106.65 },
  { id: 'tn', name: 'Tây Ninh', country: 'VN', lat: 11.31, lng: 106.1 },
  { id: 'ct', name: 'Cần Thơ', country: 'VN', lat: 10.03, lng: 105.78 },
  { id: 'vte', name: 'Vientiane', country: 'LA', lat: 17.97, lng: 102.6 },
  { id: 'svk', name: 'Savannakhet', country: 'LA', lat: 16.57, lng: 104.75 },
  { id: 'lpb', name: 'Luang Prabang', country: 'LA', lat: 19.89, lng: 102.13 },
  { id: 'pks', name: 'Pakse', country: 'LA', lat: 15.12, lng: 105.8 },
  { id: 'pnh', name: 'Phnom Penh', country: 'KH', lat: 11.56, lng: 104.92 },
  { id: 'rep', name: 'Siem Reap', country: 'KH', lat: 13.36, lng: 103.86 },
  { id: 'btb', name: 'Battambang', country: 'KH', lat: 13.1, lng: 103.2 },
  { id: 'kos', name: 'Sihanoukville', country: 'KH', lat: 10.63, lng: 103.5 },
]

// Cửa khẩu đường bộ: tọa độ phía Việt Nam
export interface Gate extends GeoPoint { name: string; country: Exclude<CountryCode, 'VN'> }
export const GATES: Gate[] = [
  { name: 'Mộc Bài – Bavet', country: 'KH', lat: 11.07, lng: 106.2 },
  { name: 'Tịnh Biên – Phnom Den', country: 'KH', lat: 10.6, lng: 104.95 },
  { name: 'Lao Bảo – Densavanh', country: 'LA', lat: 16.62, lng: 106.6 },
  { name: 'Cầu Treo – Nam Phao', country: 'LA', lat: 18.38, lng: 105.13 },
  { name: 'Tây Trang – Sop Hun', country: 'LA', lat: 21.23, lng: 102.95 },
]

// Kho / điểm nhận giao dùng khi đặt đơn (trang Tạo yêu cầu, bước 1)
export interface BookingLocation extends GeoPoint { id: string; name: string; type: 'farm' | 'club' }
export const COUNTRY_LOCATIONS: Record<CountryCode, BookingLocation[]> = {
  VN: [
    { id: 'KHO-DN', lat: 10.78, lng: 107.0, name: 'Kho Đồng Nai — Trang trại Đua ngựa Long Thành', type: 'farm' },
    { id: 'KHO-LA', lat: 10.88, lng: 106.4, name: 'Kho Long An — Trang trại Huấn luyện Mỹ Quỳnh (Đức Hòa)', type: 'farm' },
    { id: 'KHO-BD', lat: 11.0, lng: 106.65, name: 'Kho Bình Dương — Trung tâm Cưỡi ngựa Đức Hòa', type: 'farm' },
    { id: 'CLB-SG', lat: 10.79, lng: 106.74, name: 'CLB Cưỡi ngựa Sài Gòn (Saigon Pony Club - Q.2, TP.HCM)', type: 'club' },
  ],
  KH: [
    { id: 'KHO-PNH', lat: 11.56, lng: 104.92, name: 'Kho Phnom Penh — Trung tâm Kiểm dịch Động vật Phnom Penh', type: 'farm' },
    { id: 'KHO-SR', lat: 13.36, lng: 103.86, name: 'Kho Siem Reap — Trại Ngựa & Vật nuôi Angkor', type: 'farm' },
    { id: 'SAI-PNH', lat: 11.55, lng: 104.9, name: 'CLB Cưỡi ngựa Hoàng gia Phnom Penh (Phnom Penh Equestrian Club)', type: 'club' },
  ],
  LA: [
    { id: 'KHO-VTE', lat: 17.97, lng: 102.6, name: 'Kho Viêng Chăn — Trang trại Chăn nuôi & Kiểm dịch Vientiane', type: 'farm' },
    { id: 'CLB-VTE', lat: 17.98, lng: 102.62, name: 'CLB Mã cầu & Cưỡi ngựa Viêng Chăn (Vientiane Equestrian Club)', type: 'club' },
  ],
}

// Danh mục trạm nghỉ (checkpoint dọc tuyến, số mẫu để chạy thử). Hệ thống gợi ý các trạm gần đường đi nhất theo cửa khẩu đã chọn.
export interface TransitStation extends GeoPoint { name: string; area: string }
export const TRANSIT_STATIONS: TransitStation[] = [
  { name: 'Trạm nghỉ Long Thành', area: 'Đồng Nai', lat: 10.78, lng: 107.0 },
  { name: 'Trạm nghỉ Biên Hòa', area: 'Đồng Nai', lat: 10.95, lng: 106.82 },
  { name: 'Trạm nghỉ Đức Hòa', area: 'Long An', lat: 10.88, lng: 106.4 },
  { name: 'Trạm nghỉ Củ Chi', area: 'TP.HCM', lat: 10.97, lng: 106.5 },
  { name: 'Trạm nghỉ Trảng Bàng', area: 'Tây Ninh', lat: 11.03, lng: 106.37 },
  { name: 'Trạm nghỉ Mộc Bài', area: 'Tây Ninh', lat: 11.07, lng: 106.2 },
  { name: 'Trạm nghỉ Svay Rieng', area: 'Campuchia', lat: 11.09, lng: 105.8 },
  { name: 'Trạm nghỉ Neak Loeung', area: 'Campuchia', lat: 11.26, lng: 105.28 },
  { name: 'Trạm nghỉ Phnom Penh', area: 'Campuchia', lat: 11.56, lng: 104.92 },
  { name: 'Trạm nghỉ Tịnh Biên', area: 'An Giang', lat: 10.6, lng: 104.95 },
  { name: 'Trạm nghỉ Kampong Cham', area: 'Campuchia', lat: 12.0, lng: 105.46 },
  { name: 'Trạm nghỉ Phan Thiết', area: 'Bình Thuận', lat: 10.93, lng: 108.1 },
  { name: 'Trạm nghỉ Nha Trang', area: 'Khánh Hòa', lat: 12.24, lng: 109.2 },
  { name: 'Trạm nghỉ Tuy Hòa', area: 'Phú Yên', lat: 13.1, lng: 109.3 },
  { name: 'Trạm nghỉ Quy Nhơn', area: 'Bình Định', lat: 13.78, lng: 109.22 },
  { name: 'Trạm nghỉ Buôn Ma Thuột', area: 'Đắk Lắk', lat: 12.67, lng: 108.04 },
  { name: 'Trạm nghỉ Đà Nẵng', area: 'Đà Nẵng', lat: 16.05, lng: 108.2 },
  { name: 'Trạm nghỉ Huế', area: 'Thừa Thiên Huế', lat: 16.46, lng: 107.6 },
  { name: 'Trạm nghỉ Đông Hà', area: 'Quảng Trị', lat: 16.82, lng: 107.1 },
  { name: 'Trạm nghỉ Lao Bảo', area: 'Quảng Trị', lat: 16.62, lng: 106.6 },
  { name: 'Trạm nghỉ Savannakhet', area: 'Lào', lat: 16.57, lng: 104.75 },
  { name: 'Trạm nghỉ Thakhek', area: 'Lào', lat: 17.4, lng: 104.8 },
  { name: 'Trạm nghỉ Hà Tĩnh', area: 'Hà Tĩnh', lat: 18.34, lng: 105.9 },
  { name: 'Trạm nghỉ Cầu Treo', area: 'Hà Tĩnh', lat: 18.38, lng: 105.13 },
  { name: 'Trạm nghỉ Vinh', area: 'Nghệ An', lat: 18.67, lng: 105.68 },
  { name: 'Trạm nghỉ Viêng Chăn', area: 'Lào', lat: 17.97, lng: 102.6 },
]

// Điểm cứu hộ giao thông và sửa xe dọc tuyến (số mẫu để chạy thử): Coordinator gọi điểm gần chỗ xe gặp sự cố nhất
export interface RescuePoint extends GeoPoint { name: string; area: string; phone: string }
export const RESCUE_POINTS: RescuePoint[] = [
  { name: 'Cứu hộ Long Thành', area: 'Đồng Nai', phone: '0901 100 001', lat: 10.8, lng: 106.98 },
  { name: 'Cứu hộ Long Khánh', area: 'Đồng Nai', phone: '0901 100 002', lat: 10.93, lng: 107.25 },
  { name: 'Cứu hộ Biên Hòa', area: 'Đồng Nai', phone: '0901 100 003', lat: 10.94, lng: 106.84 },
  { name: 'Cứu hộ Thủ Dầu Một', area: 'Bình Dương', phone: '0901 100 004', lat: 10.99, lng: 106.66 },
  { name: 'Cứu hộ Đức Hòa', area: 'Long An', phone: '0901 100 005', lat: 10.9, lng: 106.42 },
  { name: 'Cứu hộ Củ Chi', area: 'TP.HCM', phone: '0901 100 006', lat: 10.98, lng: 106.48 },
  { name: 'Cứu hộ Trảng Bàng', area: 'Tây Ninh', phone: '0901 100 007', lat: 11.04, lng: 106.35 },
  { name: 'Cứu hộ Mộc Bài', area: 'Tây Ninh', phone: '0901 100 008', lat: 11.08, lng: 106.18 },
  { name: 'Cứu hộ Phan Thiết', area: 'Bình Thuận', phone: '0901 100 009', lat: 10.95, lng: 108.1 },
  { name: 'Cứu hộ Tịnh Biên', area: 'An Giang', phone: '0901 100 010', lat: 10.59, lng: 104.94 },
  { name: 'Cứu hộ Svay Rieng', area: 'Campuchia', phone: '+855 12 100 011', lat: 11.1, lng: 105.8 },
  { name: 'Cứu hộ Neak Loeung', area: 'Campuchia', phone: '+855 12 100 012', lat: 11.27, lng: 105.27 },
  { name: 'Cứu hộ Phnom Penh', area: 'Campuchia', phone: '+855 12 100 013', lat: 11.55, lng: 104.9 },
  { name: 'Cứu hộ Lao Bảo', area: 'Quảng Trị', phone: '0901 100 014', lat: 16.63, lng: 106.6 },
  { name: 'Cứu hộ Viêng Chăn', area: 'Lào', phone: '+856 20 100 015', lat: 17.97, lng: 102.62 },
]
