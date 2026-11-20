import { describe, expect, it } from 'vitest'
import { insertWaypoint } from './waypoints'

describe('chèn điểm đi qua vào đúng thứ tự trên đường', () => {
  // Xe ở phía nam, điểm kế tiếp ở phía bắc
  const from = { lat: 10, lng: 106 }, to = { lat: 12, lng: 106 }
  it('điểm đầu tiên nằm giữa xe và điểm kế tiếp', () => {
    expect(insertWaypoint(from, [], to, { lat: 11, lng: 106.2 })).toEqual([{ lat: 11, lng: 106.2 }])
  })
  it('bấm điểm gần xe sau điểm gần đích thì điểm đó đứng trước, đường không quay ngược', () => {
    const near = { lat: 11.6, lng: 106.2 }, early = { lat: 10.4, lng: 106.2 }
    expect(insertWaypoint(from, [near], to, early)).toEqual([early, near])
  })
  it('điểm nằm giữa hai điểm đã chọn được chèn vào giữa', () => {
    const a = { lat: 10.4, lng: 106.2 }, c = { lat: 11.6, lng: 106.2 }, b = { lat: 11, lng: 106.25 }
    expect(insertWaypoint(from, [a, c], to, b)).toEqual([a, b, c])
  })
  it('điểm sát điểm kế tiếp được đặt cuối', () => {
    const a = { lat: 10.4, lng: 106.2 }, last = { lat: 11.9, lng: 106.1 }
    expect(insertWaypoint(from, [a], to, last)).toEqual([a, last])
  })
})
