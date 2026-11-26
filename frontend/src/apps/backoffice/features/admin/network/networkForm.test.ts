import { describe, expect, it } from 'vitest'
import { locatedPoints, orderedStops, routeStopRequest } from './networkForm'

describe('dữ liệu biên tập tuyến', () => {
  it('sắp xếp điểm theo thứ tự backend gửi và giữ các lượt ghé cùng địa điểm', () => {
    const stops = [{ sequenceNumber: 3, locationId: 8 }, { sequenceNumber: 1, locationId: 8 }, { sequenceNumber: 2, locationId: 9 }]
    expect(orderedStops({ stops }).map(stop => stop.locationId)).toEqual([8, 9, 8])
    expect(stops[0].sequenceNumber).toBe(3)
  })

  it('giữ tọa độ đã lưu và bỏ qua địa điểm thiếu tọa độ', () => {
    expect(locatedPoints([{ id: 1, name: 'Hub A', latitude: 0, longitude: 106 }, { id: 2, latitude: 10 }])).toEqual([
      { id: '1', name: 'Hub A', latitude: 0, longitude: 106 },
    ])
  })

  it('gửi thứ tự mới và các mốc phút dưới dạng số, kể cả số không', () => {
    const form = new FormData()
    form.set('arrival-1', ' 45 ')
    form.set('dwell-1', '0')
    expect(routeStopRequest(form, { locationId: '8', stopType: 'REST' }, 1)).toEqual({
      sequenceNumber: 2, locationId: 8, stopType: 'REST', arrivalOffsetMinutes: 45, dwellMinutes: 0,
    })
  })
})
