// Mã microchip ngựa theo chuẩn ISO 11784/11785: đúng 15 chữ số. Bỏ dấu cách/gạch nối trước khi kiểm tra hoặc so sánh.
export const normalizeMicrochip = (value: string) => value.replace(/[\s-]/g, '').toUpperCase()

export const isIsoMicrochip = (normalized: string) => /^\d{15}$/.test(normalized)
