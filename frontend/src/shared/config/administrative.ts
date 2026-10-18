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

// Danh mục tỉnh/thành và các xã/phường trọng điểm (tọa độ chuẩn tâm xã)
export const PROVINCES_DATA: ProvinceInfo[] = [
  {
    code: 'HCM',
    name: 'TP. Hồ Chí Minh',
    country: 'VN',
    lat: 10.776,
    lng: 106.7,
    wards: [
      { name: 'Xã An Nhơn Tây (Củ Chi)', lat: 11.025, lng: 106.49 },
      { name: 'Xã Nhuận Đức (Củ Chi)', lat: 11.06, lng: 106.52 },
      { name: 'Xã Phú Hòa Đông (Củ Chi)', lat: 11.05, lng: 106.57 },
      { name: 'Xã Tân Thạnh Đông (Củ Chi)', lat: 10.98, lng: 106.61 },
      { name: 'Phường Thảo Điền (TP. Thủ Đức)', lat: 10.803, lng: 106.736 },
      { name: 'Phường Long Phước (TP. Thủ Đức)', lat: 10.82, lng: 106.84 },
      { name: 'Phường Hiệp Bình Chánh (TP. Thủ Đức)', lat: 10.83, lng: 106.72 },
      { name: 'Phường 15 (Quận 11 - Phú Thọ)', lat: 10.768, lng: 106.653 },
      { name: 'Xã Vĩnh Lộc A (Bình Chánh)', lat: 10.81, lng: 106.56 },
      { name: 'Xã Bình Lợi (Bình Chánh)', lat: 10.75, lng: 106.51 },
      { name: 'Xã Nhị Bình (Hóc Môn)', lat: 10.89, lng: 106.67 },
      { name: 'Xã Đông Thạnh (Hóc Môn)', lat: 10.88, lng: 106.63 },
      { name: 'Phường Thạnh Xuân (Quận 12)', lat: 10.87, lng: 106.68 },
    ],
  },
  {
    code: 'BDG',
    name: 'Bình Dương',
    country: 'VN',
    lat: 11.0,
    lng: 106.65,
    wards: [
      { name: 'Phường Hiệp An (Đại Nam)', lat: 11.033, lng: 106.634 },
      { name: 'Phường Định Hòa (Thủ Dầu Một)', lat: 11.02, lng: 106.64 },
      { name: 'Phường Mỹ Phước (Bến Cát)', lat: 11.16, lng: 106.61 },
      { name: 'Xã An Tây (Bến Cát)', lat: 11.13, lng: 106.54 },
      { name: 'Phường Bình Thắng (Dĩ An)', lat: 10.89, lng: 106.81 },
      { name: 'Phường An Phú (Thuận An)', lat: 10.94, lng: 106.73 },
      { name: 'Phường Uyên Hưng (Tân Uyên)', lat: 11.07, lng: 106.81 },
      { name: 'Xã Phú Giáo', lat: 11.31, lng: 106.8 },
      { name: 'Xã Dầu Tiếng', lat: 11.33, lng: 106.37 },
    ],
  },
  {
    code: 'DNI',
    name: 'Đồng Nai',
    country: 'VN',
    lat: 10.95,
    lng: 106.95,
    wards: [
      { name: 'Xã Long Phước (Long Thành)', lat: 10.74, lng: 107.01 },
      { name: 'Xã Phước Thái (Long Thành)', lat: 10.68, lng: 107.04 },
      { name: 'Xã Tam An (Long Thành)', lat: 10.81, lng: 106.95 },
      { name: 'Phường Long Bình (Biên Hòa)', lat: 10.94, lng: 106.89 },
      { name: 'Phường Trảng Dài (Biên Hòa)', lat: 10.99, lng: 106.86 },
      { name: 'Xã Hố Nai 3 (Trảng Bom)', lat: 10.98, lng: 106.96 },
      { name: 'Xã Đại Phước (Nhơn Trạch)', lat: 10.74, lng: 106.8 },
      { name: 'Xã Gia Kiệm (Thống Nhất)', lat: 10.99, lng: 107.18 },
      { name: 'Phường Xuân An (Long Khánh)', lat: 10.93, lng: 107.24 },
      { name: 'Xã Định Quán', lat: 11.2, lng: 107.35 },
    ],
  },
  {
    code: 'LAN',
    name: 'Long An',
    country: 'VN',
    lat: 10.65,
    lng: 106.3,
    wards: [
      { name: 'Xã Tân Mỹ (Mỹ Quỳnh - Đức Hòa)', lat: 10.89, lng: 106.39 },
      { name: 'Xã Đức Hòa Đông (Đức Hòa)', lat: 10.84, lng: 106.47 },
      { name: 'Xã Hiệp Hòa (Đức Hòa)', lat: 10.95, lng: 106.36 },
      { name: 'Xã An Thạnh (Bến Lức)', lat: 10.66, lng: 106.48 },
      { name: 'Xã Lương Hòa (Bến Lức)', lat: 10.75, lng: 106.45 },
      { name: 'Xã Long Hậu (Cần Giuộc)', lat: 10.63, lng: 106.72 },
      { name: 'Phường 3 (TP. Tân An)', lat: 10.53, lng: 106.41 },
      { name: 'Xã Cần Đước', lat: 10.51, lng: 106.6 },
      { name: 'Xã Kiến Tường (Mộc Hóa)', lat: 10.79, lng: 105.93 },
    ],
  },
  {
    code: 'TNH',
    name: 'Tây Ninh',
    country: 'VN',
    lat: 11.31,
    lng: 106.1,
    wards: [
      { name: 'Phường Gia Lộc (Trảng Bàng)', lat: 11.04, lng: 106.36 },
      { name: 'Phường An Tịnh (Trảng Bàng)', lat: 11.01, lng: 106.4 },
      { name: 'Xã Lợi Thuận (Cửa khẩu Mộc Bài)', lat: 11.07, lng: 106.2 },
      { name: 'Xã Tiên Thuận (Bến Cầu)', lat: 11.09, lng: 106.15 },
      { name: 'Xã Phước Trạch (Gò Dầu)', lat: 11.14, lng: 106.26 },
      { name: 'Phường 3 (TP. Tây Ninh)', lat: 11.31, lng: 106.1 },
      { name: 'Xã Suối Dây (Tân Châu)', lat: 11.55, lng: 106.22 },
      { name: 'Xã Tân Lập (Cửa khẩu Xa Mát)', lat: 11.71, lng: 105.98 },
    ],
  },
  {
    code: 'HAN',
    name: 'Hà Nội',
    country: 'VN',
    lat: 21.03,
    lng: 105.85,
    wards: [
      { name: 'Xã Tân Minh (Sóc Sơn - Trường đua)', lat: 21.28, lng: 105.86 },
      { name: 'Xã Phù Lăng (Sóc Sơn)', lat: 21.24, lng: 105.82 },
      { name: 'Xã Tản Lĩnh (Ba Vì - Trại nuôi)', lat: 21.11, lng: 105.37 },
      { name: 'Xã Yên Bài (Ba Vì)', lat: 21.04, lng: 105.42 },
      { name: 'Xã Đa Tốn (Gia Lâm)', lat: 20.98, lng: 105.94 },
      { name: 'Xã Nam Hồng (Đông Anh)', lat: 21.15, lng: 105.8 },
      { name: 'Phường Tây Hồ', lat: 21.06, lng: 105.82 },
      { name: 'Phường Hoàn Kiếm', lat: 21.03, lng: 105.85 },
      { name: 'Phường Hà Cầu (Hà Đông)', lat: 20.96, lng: 105.77 },
    ],
  },
  {
    code: 'DAN',
    name: 'Đà Nẵng',
    country: 'VN',
    lat: 16.05,
    lng: 108.2,
    wards: [
      { name: 'Phường Hòa Cường Bắc (Hải Châu)', lat: 16.04, lng: 108.21 },
      { name: 'Phường Khuê Mỹ (Ngũ Hành Sơn)', lat: 16.03, lng: 108.24 },
      { name: 'Phường Hòa Hải (Ngũ Hành Sơn)', lat: 15.99, lng: 108.26 },
      { name: 'Xã Hòa Ninh (Hòa Vang - Bà Nà)', lat: 16.02, lng: 108.06 },
      { name: 'Xã Hòa Khương (Hòa Vang)', lat: 15.95, lng: 108.12 },
    ],
  },
  {
    code: 'KHA',
    name: 'Khánh Hòa',
    country: 'VN',
    lat: 12.24,
    lng: 109.2,
    wards: [
      { name: 'Phường Lộc Thọ (Nha Trang)', lat: 12.24, lng: 109.19 },
      { name: 'Xã Vĩnh Phương (Nha Trang)', lat: 12.29, lng: 109.15 },
      { name: 'Xã Diên Hòa (Diên Khánh)', lat: 12.26, lng: 109.07 },
      { name: 'Phường Cam Nghĩa (Cam Ranh)', lat: 12.01, lng: 109.14 },
    ],
  },
  {
    code: 'LDG',
    name: 'Lâm Đồng',
    country: 'VN',
    lat: 11.94,
    lng: 108.44,
    wards: [
      { name: 'Phường 3 (Đà Lạt - Hồ Tuyền Lâm)', lat: 11.905, lng: 108.435 },
      { name: 'Phường 8 (Đà Lạt - Thung lũng Tình Yêu)', lat: 11.97, lng: 108.44 },
      { name: 'Xã Hiệp Thạnh (Đức Trọng)', lat: 11.75, lng: 108.38 },
      { name: 'Xã Lộc An (Bảo Lâm)', lat: 11.54, lng: 107.94 },
      { name: 'Phường B\'Lao (Bảo Lộc)', lat: 11.53, lng: 107.81 },
    ],
  },
  {
    code: 'BTH',
    name: 'Bình Thuận',
    country: 'VN',
    lat: 10.93,
    lng: 108.1,
    wards: [
      { name: 'Phường Phú Thủy (Phan Thiết)', lat: 10.94, lng: 108.11 },
      { name: 'Phường Mũi Né (Phan Thiết)', lat: 10.93, lng: 108.29 },
      { name: 'Xã Tân Lập (Hàm Thuận Nam)', lat: 10.95, lng: 107.98 },
      { name: 'Xã Tân Minh (Hàm Tân)', lat: 10.82, lng: 107.68 },
    ],
  },
  {
    code: 'BTE',
    name: 'Bến Tre',
    country: 'VN',
    lat: 10.24,
    lng: 106.38,
    wards: [
      { name: 'Phường An Hội (TP. Bến Tre)', lat: 10.24, lng: 106.38 },
      { name: 'Xã Châu Thành', lat: 10.3, lng: 106.35 },
      { name: 'Xã Ba Tri', lat: 10.05, lng: 106.6 },
    ],
  },
  {
    code: 'TGG',
    name: 'Tiền Giang',
    country: 'VN',
    lat: 10.36,
    lng: 106.36,
    wards: [
      { name: 'Phường 1 (Mỹ Tho)', lat: 10.36, lng: 106.36 },
      { name: 'Xã Châu Thành', lat: 10.41, lng: 106.28 },
      { name: 'Xã Cái Bè', lat: 10.33, lng: 105.99 },
    ],
  },
  {
    code: 'CTH',
    name: 'Cần Thơ',
    country: 'VN',
    lat: 10.03,
    lng: 105.78,
    wards: [
      { name: 'Phường Ninh Kiều', lat: 10.03, lng: 105.78 },
      { name: 'Phường Bình Thủy', lat: 10.07, lng: 105.74 },
      { name: 'Xã Phong Điền', lat: 9.98, lng: 105.67 },
      { name: 'Xã Ô Môn', lat: 10.12, lng: 105.62 },
    ],
  },
  {
    code: 'AGI',
    name: 'An Giang',
    country: 'VN',
    lat: 10.38,
    lng: 105.43,
    wards: [
      { name: 'Phường Mỹ Bình (Long Xuyên)', lat: 10.38, lng: 105.43 },
      { name: 'Phường Châu Phú B (Châu Đốc)', lat: 10.7, lng: 105.12 },
      { name: 'Xã Tịnh Biên (Cửa khẩu Tịnh Biên)', lat: 10.6, lng: 104.95 },
      { name: 'Xã Tri Tôn', lat: 10.42, lng: 105.0 },
    ],
  },
  {
    code: 'QTR',
    name: 'Quảng Trị',
    country: 'VN',
    lat: 16.75,
    lng: 107.1,
    wards: [
      { name: 'Phường 1 (Đông Hà)', lat: 16.82, lng: 107.1 },
      { name: 'Xã Lao Bảo (Cửa khẩu Lao Bảo)', lat: 16.62, lng: 106.6 },
      { name: 'Xã Cam Lộ', lat: 16.8, lng: 107.0 },
    ],
  },
  {
    code: 'HTI',
    name: 'Hà Tĩnh',
    country: 'VN',
    lat: 18.34,
    lng: 105.9,
    wards: [
      { name: 'Phường Trần Phú (TP. Hà Tĩnh)', lat: 18.34, lng: 105.9 },
      { name: 'Xã Sơn Kim 1 (Cửa khẩu Cầu Treo)', lat: 18.38, lng: 105.13 },
      { name: 'Xã Hương Sơn', lat: 18.5, lng: 105.25 },
    ],
  },
  {
    code: 'NAN',
    name: 'Nghệ An',
    country: 'VN',
    lat: 18.67,
    lng: 105.68,
    wards: [
      { name: 'Phường Lê Mao (Vinh)', lat: 18.67, lng: 105.68 },
      { name: 'Phường Cửa Lò', lat: 18.8, lng: 105.72 },
      { name: 'Xã Nam Đàn', lat: 18.7, lng: 105.5 },
    ],
  },
  {
    code: 'QNI',
    name: 'Quảng Ninh',
    country: 'VN',
    lat: 20.95,
    lng: 107.08,
    wards: [
      { name: 'Phường Hồng Gai (Hạ Long)', lat: 20.95, lng: 107.08 },
      { name: 'Phường Bãi Cháy (Hạ Long)', lat: 20.96, lng: 107.02 },
      { name: 'Phường Cẩm Phả', lat: 21.01, lng: 107.28 },
      { name: 'Phường Móng Cái', lat: 21.53, lng: 107.97 },
    ],
  },
  // Nước bạn: Campuchia (Tuyến quốc tế)
  {
    code: 'KH-PNH',
    name: 'Phnom Penh (Campuchia)',
    country: 'KH',
    lat: 11.56,
    lng: 104.92,
    wards: [
      { name: 'Sangkat Tuol Kouk (Trung tâm kiểm dịch)', lat: 11.56, lng: 104.92 },
      { name: 'Sangkat Chamkar Mon (CLB Hoàng gia)', lat: 11.54, lng: 104.93 },
      { name: 'Sangkat Dangkao', lat: 11.51, lng: 104.88 },
    ],
  },
  {
    code: 'KH-SVR',
    name: 'Svay Rieng (Campuchia - gần Mộc Bài)',
    country: 'KH',
    lat: 11.09,
    lng: 105.8,
    wards: [
      { name: 'Krong Bavet (Cửa khẩu Bavet)', lat: 11.07, lng: 106.18 },
      { name: 'Sangkat Svay Rieng', lat: 11.09, lng: 105.8 },
    ],
  },
  {
    code: 'KH-REP',
    name: 'Siem Reap (Campuchia)',
    country: 'KH',
    lat: 13.36,
    lng: 103.86,
    wards: [
      { name: 'Sangkat Svay Dangkum (Angkor)', lat: 13.36, lng: 103.86 },
      { name: 'Sangkat Sala Kamreuk', lat: 13.35, lng: 103.87 },
    ],
  },
  // Nước bạn: Lào (Tuyến quốc tế)
  {
    code: 'LA-VTE',
    name: 'Viêng Chăn - Vientiane (Lào)',
    country: 'LA',
    lat: 17.97,
    lng: 102.6,
    wards: [
      { name: 'Ban Nongbone (CLB Cưỡi ngựa Vientiane)', lat: 17.98, lng: 102.62 },
      { name: 'Ban Phonsinuan', lat: 17.96, lng: 102.63 },
      { name: 'Ban Thanaleng (Cầu Hữu Nghị)', lat: 17.89, lng: 102.71 },
    ],
  },
  {
    code: 'LA-SVK',
    name: 'Savannakhet (Lào - gần Lao Bảo)',
    country: 'LA',
    lat: 16.57,
    lng: 104.75,
    wards: [
      { name: 'Kaysone Phomvihane', lat: 16.56, lng: 104.75 },
      { name: 'Muang Outhoumphone (Densavanh)', lat: 16.61, lng: 106.58 },
    ],
  },
]

// Hàm tìm tọa độ GPS ước tính theo Tỉnh và Xã
export function findAdminCoords(provinceNameOrCode: string, wardName?: string): { lat: number; lng: number } | undefined {
  if (!provinceNameOrCode) return undefined
  const p = PROVINCES_DATA.find(
    item => item.code.toLowerCase() === provinceNameOrCode.toLowerCase() ||
      item.name.toLowerCase().includes(provinceNameOrCode.toLowerCase()) ||
      provinceNameOrCode.toLowerCase().includes(item.name.toLowerCase())
  )
  if (!p) return undefined
  if (wardName) {
    const w = p.wards.find(
      item => item.name.toLowerCase().includes(wardName.toLowerCase()) ||
        wardName.toLowerCase().includes(item.name.toLowerCase())
    )
    if (w) return { lat: w.lat, lng: w.lng }
  }
  return { lat: p.lat, lng: p.lng }
}

// Ghép chuỗi địa chỉ đầy đủ: [Tên trại/Số nhà], [Xã/Phường], [Tỉnh/Thành]
export function buildFullAddress(venueOrDetail: string, ward: string, province: string): string {
  const parts = [venueOrDetail.trim(), ward.trim(), province.trim()].filter(Boolean)
  return parts.join(', ')
}
