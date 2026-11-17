import { KH_LA_PROVINCES } from './khLaAdministrative'
import { VN_PROVINCES_2025 } from './vnAdministrative2025'

// Danh mục hành chính 2 cấp (Cấp Tỉnh và Cấp Xã) theo mô hình chính quyền địa phương mới
// Phục vụ ước tính cước, tính km OSRM và gợi ý trạm dừng nghỉ chuẩn xác theo tọa độ tâm xã/phường

export interface WardInfo {
  name: string
  lat: number
  lng: number
}

export interface ProvinceInfo {
  code: string
  name: string
  country: 'VN' | 'KH' | 'LA'
  lat: number
  lng: number
  wards: WardInfo[]
}

export interface HorseVenue {
  id: string
  name: string
  provinceCode: string
  wardName: string
  address: string
  lat: number
  lng: number
}

// Địa điểm trang trại, trường đua và câu lạc bộ cưỡi ngựa quen thuộc (chọn nhanh 1 chạm)
export const FAMOUS_HORSE_VENUES: HorseVenue[] = [
  {
    id: 'vn-dn-longthanh',
    name: 'Trang trại Đua ngựa Long Thành (Kho Đồng Nai)',
    provinceCode: 'DNI',
    wardName: 'Xã Long Phước',
    address: 'Khu chuồng trại huấn luyện Long Thành, Xã Long Phước',
    lat: 10.78,
    lng: 107.0,
  },
  {
    id: 'vn-la-myquynh',
    name: 'Trang trại Huấn luyện Mỹ Quỳnh Safari (Đức Hòa, Long An)',
    provinceCode: 'LAN',
    wardName: 'Xã Tân Mỹ',
    address: 'Trang trại huấn luyện ngựa Mỹ Quỳnh, Xã Tân Mỹ',
    lat: 10.88,
    lng: 106.4,
  },
  {
    id: 'vn-hcm-cuchi',
    name: 'Trang trại & Trung tâm Cưỡi ngựa Củ Chi (TP.HCM)',
    provinceCode: 'HCM',
    wardName: 'Xã An Nhơn Tây',
    address: 'Khu bảo tồn và huấn luyện ngựa Củ Chi, Xã An Nhơn Tây',
    lat: 11.02,
    lng: 106.49,
  },
  {
    id: 'vn-hcm-saigonpony',
    name: 'CLB Cưỡi ngựa Sài Gòn — Saigon Pony Club (TP.HCM)',
    provinceCode: 'HCM',
    wardName: 'Phường Thảo Điền',
    address: 'Số 164 Đường số 5, Phường Thảo Điền',
    lat: 10.79,
    lng: 106.74,
  },
  {
    id: 'vn-hcm-phutho',
    name: 'Trung tâm Thể thao & Câu lạc bộ Phú Thọ (TP.HCM)',
    provinceCode: 'HCM',
    wardName: 'Phường 15',
    address: 'Số 2-4 Lê Đại Hành, Phường 15',
    lat: 10.768,
    lng: 106.653,
  },
  {
    id: 'vn-bd-dainam',
    name: 'Trường đua Đại Nam — Khu Du lịch & Đua ngựa (Bình Dương)',
    provinceCode: 'BDG',
    wardName: 'Phường Hiệp An',
    address: 'Đại lộ Bình Dương, Phường Hiệp An',
    lat: 11.033,
    lng: 106.634,
  },
  {
    id: 'vn-tn-trangbang',
    name: 'Trang trại Nuôi & Huấn luyện Ngựa Trảng Bàng (Tây Ninh)',
    provinceCode: 'TNH',
    wardName: 'Phường Gia Lộc',
    address: 'Khu trại ngựa ven ĐT787, Phường Gia Lộc',
    lat: 11.04,
    lng: 106.36,
  },
  {
    id: 'vn-hn-socson',
    name: 'Dự án Trường đua & Trại ngựa Sóc Sơn (Hà Nội)',
    provinceCode: 'HAN',
    wardName: 'Xã Tân Minh',
    address: 'Khu liên hợp thể thao đua ngựa Sóc Sơn, Xã Tân Minh',
    lat: 21.28,
    lng: 105.86,
  },
  {
    id: 'vn-dl-dalat',
    name: 'CLB Cưỡi ngựa Đồi Mộng Mơ & Hồ Tuyền Lâm (Đà Lạt)',
    provinceCode: 'LDG',
    wardName: 'Phường 3',
    address: 'Khu vực hồ Tuyền Lâm, Phường 3',
    lat: 11.905,
    lng: 108.435,
  },
  {
    id: 'kh-pnh-national',
    name: 'Trung tâm Kiểm dịch & Trại ngựa Quốc gia Phnom Penh',
    provinceCode: 'KH-PNH',
    wardName: 'Sangkat Tuol Kouk',
    address: 'Trung tâm Kiểm dịch Động vật Phnom Penh',
    lat: 11.56,
    lng: 104.92,
  },
  {
    id: 'la-vte-club',
    name: 'CLB Mã cầu & Cưỡi ngựa Viêng Chăn (Vientiane Equestrian Club)',
    provinceCode: 'LA-VTE',
    wardName: 'Ban Nongbone',
    address: 'Khu huấn luyện ngoại ô Vientiane, Ban Nongbone',
    lat: 17.98,
    lng: 102.62,
  },
]

// Danh mục để khách chọn: Việt Nam theo 34 tỉnh mới (Tỉnh → Phường/Xã), Campuchia và Lào theo Tỉnh → Huyện.
// Ở Campuchia/Lào, trường "wards" chứa huyện (quận/khan/muang).
export const PROVINCES_DATA: ProvinceInfo[] = [
  ...VN_PROVINCES_2025.map(p => ({
    code: p.code,
    name: p.name,
    country: 'VN' as const,
    lat: p.lat,
    lng: p.lng,
    wards: p.wards.map(name => ({ name, lat: p.coords[name]?.[0] ?? p.lat, lng: p.coords[name]?.[1] ?? p.lng })),
  })),
  ...KH_LA_PROVINCES,
]

// Tách địa chỉ dạng "chi tiết, Phường/Xã, Tỉnh/Thành" (đúng như buildFullAddress ghép) thành từng phần.
// So khớp nguyên cả phần giữa hai dấu phẩy, không so chuỗi con (tránh "Xã An" khớp nhầm "Xã An Phú").
export function splitAddress(address: string, country: 'VN' | 'KH' | 'LA'): { provinceCode: string; wardName: string; detail: string } {
  const parts = (address || '').split(',').map(part => part.trim()).filter(Boolean)
  const same = (a: string, b: string) => a.toLocaleLowerCase() === b.toLocaleLowerCase()
  const provinces = PROVINCES_DATA.filter(p => p.country === country)
  const province = provinces.find(p => parts.some(part => same(part, p.name)))
    // Tên địa điểm có sẵn (vd "Kho Đồng Nai — ...") chỉ cho biết tỉnh, chưa biết xã
    ?? provinces.find(p => {
      const short = p.name.replace(/^(Thành phố|Tỉnh)\s+/, '').toLocaleLowerCase()
      return (address || '').toLocaleLowerCase().includes(short)
    })
  if (!province) return { provinceCode: '', wardName: '', detail: (address || '').trim() }
  const ward = province.wards.find(w => parts.some(part => same(part, w.name)))?.name
    ?? (parts.some(part => same(part, 'Khác')) ? 'Khác' : '')
  const detail = parts.filter(part => !same(part, province.name) && !(ward && same(part, ward))).join(', ')
  return { provinceCode: province.code, wardName: ward, detail }
}

// Ghép chuỗi địa chỉ đầy đủ: [Tên trại/Số nhà], [Xã/Phường], [Tỉnh/Thành]
export function buildFullAddress(venueOrDetail: string, ward: string, province: string): string {
  const parts = [venueOrDetail.trim(), ward.trim(), province.trim()].filter(Boolean)
  return parts.join(', ')
}
