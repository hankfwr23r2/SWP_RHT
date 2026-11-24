import { afterEach, describe, expect, it, vi } from 'vitest'
import { departuresApi } from '@shared/api/departures'
import { managerOrdersApi } from '@shared/api/managerOrders'
import { refundsApi } from '@shared/api/refunds'
import { countLabel, loadManagerWork, resetManagerWork } from './managerWork'

describe('việc đang chờ của quản lý', () => {
  afterEach(() => { resetManagerWork(); vi.restoreAllMocks() })

  it('đếm từng loại việc, nguồn lỗi coi như 0 và dùng lại trong 30 giây', async () => {
    const summaries = vi.spyOn(managerOrdersApi, 'summaries').mockResolvedValue([{ id: 1 }, { id: 2 }])
    vi.spyOn(managerOrdersApi, 'missingDocuments').mockRejectedValue(new Error('lỗi'))
    vi.spyOn(departuresApi, 'decisions').mockResolvedValue([{ id: 1, confirmable: true }, { id: 2, confirmable: false }])
    vi.spyOn(refundsApi, 'list').mockResolvedValue([{ id: 1 }])
    expect(await loadManagerWork(1_000)).toEqual({ quoting: 2, documents: 0, decisions: 1, refunds: 1 })
    await loadManagerWork(20_000)
    expect(summaries).toHaveBeenCalledTimes(1)
    await loadManagerWork(40_000)
    expect(summaries).toHaveBeenCalledTimes(2)
  })

  it('đủ một trang thì ghi còn nữa', () => {
    expect(countLabel(3)).toBe('3')
    expect(countLabel(50)).toBe('50+')
  })
})
