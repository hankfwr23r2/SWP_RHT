// Chỉ đường theo đường giao thông: giải mã đường Google, đọc kết quả Google và OSRM, thứ tự dự phòng, bộ nhớ đệm.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearRoadCache, decodePolyline, roadNote, roadRoute } from './routing'

const A = { lat: 10.78, lng: 107.0 }, B = { lat: 11.56, lng: 104.92 }, GATE = { lat: 11.07, lng: 106.2 }
const json = (body: unknown, ok = true) => ({ ok, json: async () => body }) as Response
const osrmBody = { routes: [{ distance: 280_000, duration: 14_400, geometry: { coordinates: [[107.0, 10.78], [106.2, 11.07], [104.92, 11.56]] } }] }
const googleBody = { routes: [{ duration: '16200s', distanceMeters: 285_000, polyline: { encodedPolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@' } }] }
const FUTURE = Math.floor((Date.now() + 30 * 86_400_000) / 3_600_000) * 3_600_000 + 10 * 60_000 // giữa giờ, để cộng thêm vài phút không sang giờ khác

const stub = (handler: (url: string, init?: RequestInit) => Response | Promise<Response>) => {
  const fn = vi.fn(async (url: string | URL | Request, init?: RequestInit) => handler(String(url), init))
  vi.stubGlobal('fetch', fn)
  return fn
}
beforeEach(() => clearRoadCache())
afterEach(() => vi.unstubAllGlobals())

describe('giải mã đường mã hóa kiểu Google', () => {
  it('đọc đúng ví dụ trong tài liệu của Google', () => {
    expect(decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]])
    expect(decodePolyline('')).toEqual([])
  })
})

describe('OSRM (không cần khóa, không tính giao thông)', () => {
  it('đổi kết quả sang đường [vĩ độ, kinh độ], km và giờ lái', async () => {
    const f = stub(() => json(osrmBody))
    const r = await roadRoute([A, GATE, B], FUTURE, { googleKey: '' })
    expect(r).toMatchObject({ source: 'osrm', traffic: false, km: 280, hours: 4 })
    expect(r!.path[0]).toEqual([10.78, 107.0])
    expect(String(f.mock.calls[0][0])).toContain('107,10.78;106.2,11.07;104.92,11.56')
  })
})

describe('Google Routes (có khóa, tính giao thông theo giờ khởi hành)', () => {
  it('gửi giờ khởi hành ở tương lai, các điểm trung gian, và đọc thời gian có giao thông', async () => {
    const f = stub(() => json(googleBody))
    const r = await roadRoute([A, GATE, B], FUTURE, { googleKey: 'KEY' })
    expect(r).toMatchObject({ source: 'google', traffic: true, km: 285, hours: 4.5 })
    expect(r!.path).toHaveLength(3)
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toContain('routes.googleapis.com')
    expect((init.headers as Record<string, string>)['X-Goog-Api-Key']).toBe('KEY')
    const body = JSON.parse(String(init.body))
    expect(body.intermediates).toHaveLength(1)
    expect(body.routingPreference).toBe('TRAFFIC_AWARE')
    expect(body.departureTime).toBe(new Date(FUTURE).toISOString())
  })
  it('giờ khởi hành đã qua thì không gửi departureTime (Google chỉ dự báo giờ ở tương lai)', async () => {
    const f = stub(() => json(googleBody))
    await roadRoute([A, B], Date.now() - 3_600_000, { googleKey: 'KEY' })
    expect(JSON.parse(String((f.mock.calls[0] as [string, RequestInit])[1].body)).departureTime).toBeUndefined()
  })
})

describe('thứ tự dự phòng', () => {
  it('Google lỗi (hết hạn mức, sai khóa) thì dùng OSRM', async () => {
    const f = stub(url => (url.includes('googleapis') ? json({}, false) : json(osrmBody)))
    expect((await roadRoute([A, B], FUTURE, { googleKey: 'KEY' }))!.source).toBe('osrm')
    expect(f).toHaveBeenCalledTimes(2)
  })
  it('lỗi mạng ở Google cũng dùng OSRM; không có khóa thì không gọi Google', async () => {
    stub(url => { if (url.includes('googleapis')) throw new Error('mất mạng'); return json(osrmBody) })
    expect((await roadRoute([A, B], FUTURE, { googleKey: 'KEY' }))!.source).toBe('osrm')
    clearRoadCache()
    const f = stub(() => json(osrmBody))
    await roadRoute([A, B], FUTURE, { googleKey: '' })
    expect(f.mock.calls.every(c => !String(c[0]).includes('googleapis'))).toBe(true)
  })
  it('cả hai đều lỗi thì trả về null để nơi gọi vẽ nét thẳng; dưới 2 điểm cũng null', async () => {
    stub(() => json({}, false))
    expect(await roadRoute([A, B], FUTURE, { googleKey: 'KEY' })).toBeNull()
    expect(await roadRoute([A], FUTURE)).toBeNull()
  })
  it('cùng đầu vào trong cùng một giờ thì dùng bộ nhớ đệm, không gọi lại', async () => {
    const f = stub(() => json(osrmBody))
    await roadRoute([A, B], FUTURE, { googleKey: '' })
    await roadRoute([A, B], FUTURE + 60_000, { googleKey: '' })
    expect(f).toHaveBeenCalledTimes(1)
  })
})

describe('ghi chú nguồn thời gian lái', () => {
  it('nói rõ nguồn: đang tính, Google, OSRM, ước lượng', async () => {
    stub(() => json(osrmBody))
    const osrm = await roadRoute([A, B], FUTURE, { googleKey: '' })
    expect(roadNote(undefined, true)).toMatch(/đang tính/)
    expect(roadNote(osrm, false)).toMatch(/chưa tính giao thông/)
    expect(roadNote({ ...osrm!, source: 'google', traffic: true }, false)).toMatch(/Google, có tính giao thông/)
    expect(roadNote(null, false)).toMatch(/ước lượng/)
  })
})
