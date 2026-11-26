// Ảnh thật dùng ở trang chủ, lấy từ Wikimedia Commons (không dùng ảnh tự tạo). Ghi nguồn đúng giấy phép.
// src: rộng 1280px cho nền lớn; small: rộng 640px cho thẻ và điện thoại
export interface Photo { src: string; small: string; alt: string; author: string; license: string; page: string }
const commons = (file: string) => `https://commons.wikimedia.org/wiki/File:${encodeURIComponent(file.replace(/ /g, '_'))}`
export const PHOTOS = {
  race: { src: '/images/race.webp', small: '/images/race-sm.webp', alt: 'Ngựa đua tại trường đua Pimlico trước giờ thi đấu', author: 'Maryland GovPics', license: 'CC BY 2.0', page: commons('142nd Preakness Stakes Pimlico Race Course (34784769366).jpg') },
  transport: { src: '/images/transport.webp', small: '/images/transport-sm.webp', alt: 'Xe chuyên dụng chở ngựa đua trên đường', author: 'Syced', license: 'CC0', page: commons('Horse transport long vehicle.jpg') },
  checkup: { src: '/images/checkup.webp', small: '/images/checkup-sm.webp', alt: 'Bác sĩ thú y khám móng ngựa', author: 'Sgt. 1st Class Walter Van Ochten (Quân đội Hoa Kỳ)', license: 'Phạm vi công cộng', page: commons("A Panamanian resident shows his horse's hoof to U.S. Army Capt. John Turco, a veterinarian with the 719th Medical Detachment Veterinary Services based in Fort Sheridan, Ill., during an examination in Cantina 052913-Z-PQ189-0543.jpg") },
  stable: { src: '/images/stable.webp', small: '/images/stable-sm.webp', alt: 'Dãy chuồng ngựa có vách ngăn riêng từng con', author: 'Jebulon', license: 'CC0', page: commons('Stalles musée vivant cheval Chantilly.jpg') },
  trailers: { src: '/images/trailers.webp', small: '/images/trailers-sm.webp', alt: 'Các xe moóc chở ngựa', author: 'Kahvilokki', license: 'CC0', page: commons('20200509 Hevostrailereita.jpg') },
} satisfies Record<string, Photo>
