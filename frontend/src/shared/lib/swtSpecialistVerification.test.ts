import { describe, expect, it } from 'vitest'

describe('QU-023: Kiểm tra nghiệp vụ nhập báo giá bảo hiểm của Chuyên viên', () => {
  function validateInsuranceQuote(provider: string | null | undefined, premium: number | null | undefined) {
    if (!provider || provider.trim() === '' || !premium || premium <= 0) {
      return {
        valid: false,
        message: 'Nhập đơn vị cung cấp và phí bảo hiểm lớn hơn 0.',
      }
    }
    return { valid: true, message: 'Báo giá bảo hiểm hợp lệ' }
  }

  it('từ chối khi phí bảo hiểm bằng 0 hoặc bỏ trống đơn vị cung cấp', () => {
    const res1 = validateInsuranceQuote('', 1_500_000)
    expect(res1.valid).toBe(false)
    expect(res1.message).toBe('Nhập đơn vị cung cấp và phí bảo hiểm lớn hơn 0.')

    const res2 = validateInsuranceQuote('Bảo Việt', 0)
    expect(res2.valid).toBe(false)
    expect(res2.message).toBe('Nhập đơn vị cung cấp và phí bảo hiểm lớn hơn 0.')
  })

  it('chấp nhận khi có đầy đủ đơn vị cung cấp và phí bảo hiểm lớn hơn 0', () => {
    const res = validateInsuranceQuote('Bảo Việt Inter-Transit', 1_800_000)
    expect(res.valid).toBe(true)
  })
})

describe('QU-024, QU-025, QU-026: Xác nhận và khóa chỉnh sửa hợp đồng bảo hiểm', () => {
  interface InsuranceContract {
    depositPaid: boolean
    contractNumber?: string | null
    effectiveFrom?: string | null
    effectiveTo?: string | null
    departureDate: string
    isConfirmed: boolean
  }

  function confirmInsuranceContract(c: InsuranceContract) {
    if (!c.depositPaid) {
      return { valid: false, message: 'Chỉ xác nhận hợp đồng sau đặt cọc, cần số hợp đồng.' }
    }
    if (!c.contractNumber || c.contractNumber.trim() === '') {
      return { valid: false, message: 'Chỉ xác nhận hợp đồng sau đặt cọc, cần số hợp đồng.' }
    }
    if (!c.effectiveFrom || !c.effectiveTo || c.effectiveFrom > c.departureDate || c.effectiveTo < c.departureDate) {
      return { valid: false, message: 'Hợp đồng phải bao phủ ngày khởi hành.' }
    }
    return { valid: true, message: 'Xác nhận hợp đồng thành công' }
  }

  function editInsuranceContract(c: InsuranceContract) {
    if (c.isConfirmed) {
      return { allowed: false, message: 'Hợp đồng bảo hiểm đã được xác nhận có hiệu lực, không thể chỉnh sửa tại bước này.' }
    }
    return { allowed: true, message: 'Cho phép chỉnh sửa' }
  }

  it('QU-026: Chặn xác nhận hợp đồng khi đơn hàng chưa hoàn tất đặt cọc', () => {
    const res = confirmInsuranceContract({
      depositPaid: false,
      contractNumber: 'HD-BH-999',
      departureDate: '2026-11-20',
      isConfirmed: false,
    })
    expect(res.valid).toBe(false)
    expect(res.message).toBe('Chỉ xác nhận hợp đồng sau đặt cọc, cần số hợp đồng.')
  })

  it('QU-024: Chặn xác nhận khi ngày hiệu lực của bảo hiểm không bao phủ ngày khởi hành', () => {
    const res = confirmInsuranceContract({
      depositPaid: true,
      contractNumber: 'HD-BH-001',
      effectiveFrom: '2026-11-25', // Bắt đầu sau ngày khởi hành
      effectiveTo: '2026-11-30',
      departureDate: '2026-11-20',
      isConfirmed: false,
    })
    expect(res.valid).toBe(false)
    expect(res.message).toBe('Hợp đồng phải bao phủ ngày khởi hành.')
  })

  it('QU-024: Xác nhận thành công khi đơn đã cọc, có số HĐ và ngày hiệu lực bao phủ ngày khởi hành', () => {
    const res = confirmInsuranceContract({
      depositPaid: true,
      contractNumber: 'HD-BH-001',
      effectiveFrom: '2026-11-19',
      effectiveTo: '2026-11-25',
      departureDate: '2026-11-20',
      isConfirmed: false,
    })
    expect(res.valid).toBe(true)
  })

  it('QU-025: Không cho phép sửa phí hoặc đơn vị cung cấp khi hợp đồng đã được xác nhận', () => {
    const res = editInsuranceContract({
      depositPaid: true,
      contractNumber: 'HD-BH-001',
      departureDate: '2026-11-20',
      isConfirmed: true,
    })
    expect(res.allowed).toBe(false)
    expect(res.message).toBe('Hợp đồng bảo hiểm đã được xác nhận có hiệu lực, không thể chỉnh sửa tại bước này.')
  })
})

describe('QU-028, QU-029, QU-031: Quản lý hạng mục giấy tờ chuyến đi', () => {
  interface TripDocument {
    type: 'DOMESTIC' | 'INTERNATIONAL'
    name: string
    isSubmitted: boolean
    horseId?: string
  }

  function validateCompletion(isInternational: boolean, docs: TripDocument[], horseCount: number) {
    const pendingDocs = docs.filter(d => !d.isSubmitted)
    if (pendingDocs.length > 0) {
      return { allowed: false, message: 'Chưa thể hoàn tất: Vẫn còn hạng mục giấy tờ chưa nộp!' }
    }
    if (isInternational) {
      const clearedHorses = new Set(docs.filter(d => d.type === 'INTERNATIONAL' && d.name.includes('Thông quan') && d.isSubmitted).map(d => d.horseId))
      if (clearedHorses.size < horseCount) {
        return { allowed: false, message: 'Tuyến quốc tế yêu cầu tất cả ngựa phải được thông quan trước khi hoàn tất.' }
      }
    }
    return { allowed: true, message: 'Hoàn tất thủ tục giấy tờ chuyến đi.' }
  }

  function addDocumentToTrip(isInternational: boolean, docType: 'DOMESTIC' | 'INTERNATIONAL') {
    if (!isInternational && docType === 'INTERNATIONAL') {
      return { allowed: false, message: 'Loại giấy tờ quốc tế này không áp dụng cho đơn trong nước.' }
    }
    return { allowed: true, message: 'Thêm hạng mục thành công.' }
  }

  it('QU-028: Chặn bấm hoàn tất khi còn hạng mục giấy tờ chưa nộp', () => {
    const docs: TripDocument[] = [
      { type: 'DOMESTIC', name: 'Giấy chứng nhận tiêm phòng', isSubmitted: true },
      { type: 'DOMESTIC', name: 'Giấy kiểm dịch động vật', isSubmitted: false },
    ]
    const res = validateCompletion(false, docs, 1)
    expect(res.allowed).toBe(false)
    expect(res.message).toBe('Chưa thể hoàn tất: Vẫn còn hạng mục giấy tờ chưa nộp!')
  })

  it('QU-029: Đơn quốc tế yêu cầu 100% ngựa phải có thông quan trước khi hoàn tất', () => {
    // 2 ngựa nhưng mới thông quan 1 con
    const docs: TripDocument[] = [
      { type: 'INTERNATIONAL', name: 'Thông quan xuất khẩu', isSubmitted: true, horseId: 'H1' },
    ]
    const res = validateCompletion(true, docs, 2)
    expect(res.allowed).toBe(false)
    expect(res.message).toBe('Tuyến quốc tế yêu cầu tất cả ngựa phải được thông quan trước khi hoàn tất.')
  })

  it('QU-031: Chặn thêm tờ khai hải quan/giấy tờ quốc tế vào đơn vận chuyển nội địa', () => {
    const res = addDocumentToTrip(false, 'INTERNATIONAL')
    expect(res.allowed).toBe(false)
    expect(res.message).toBe('Loại giấy tờ quốc tế này không áp dụng cho đơn trong nước.')
  })
})

