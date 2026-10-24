// Chỉ đường theo đường giao thông: đọc kết quả OSRM, dự phòng ngoại tuyến, bộ nhớ đệm.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearRoadCache, decodePolyline, roadNote, roadRoute } from './routing'

const A = { lat: 10.78, lng: 107.0 }, B = { lat: 11.56, lng: 104.92 }, GATE = { lat: 11.07, lng: 106.2 }
const json = (body: unknown, ok = true) => ({ ok, json: async () => body }) as Response
const osrmBody = { routes: [{ distance: 280_000, duration: 14_400, geometry: { coordinates: [[107.0, 10.78], [106.2, 11.07], [104.92, 11.56]] } }] }
const FUTURE = Math.floor((Date.now() + 30 * 86_400_000) / 3_600_000) * 3_600_000 + 10 * 60_000

const stub = (handler: (url: string, init?: RequestInit) => Response | Promise<Response>) => {
  const fn = vi.fn(async (url: string | URL | Request, init?: RequestInit) => handler(String(url), init))
  vi.stubGlobal('fetch', fn)
  return fn
}
beforeEach(() => clearRoadCache())
afterEach(() => vi.unstubAllGlobals())

describe('giải mã đường mã hóa', () => {
  it('đọc đúng ví dụ polyline', () => {
    expect(decodePolyline('_p~iF~ps|U_ulLnnqC_mqNvxq`@')).toEqual([[38.5, -120.2], [40.7, -120.95], [43.252, -126.453]])
    expect(decodePolyline('')).toEqual([])
  })
})

describe('OSRM (mã nguồn mở, không tốn phí)', () => {
  it('đổi kết quả sang đường [vĩ độ, kinh độ], km và giờ lái', async () => {
    const f = stub(() => json(osrmBody))
    const r = await roadRoute([A, GATE, B], FUTURE)
    expect(r).toMatchObject({ source: 'osrm', traffic: false, km: 280, hours: 4 })
    expect(r!.path[0]).toEqual([10.78, 107.0])
    expect(String(f.mock.calls[0][0])).toContain('107,10.78;106.2,11.07;104.92,11.56')
  })
})

describe('dự phòng ngoại tuyến khi OSRM lỗi hoặc mất mạng', () => {
  it('OSRM lỗi thì tự động dùng công thức hình học đường bộ (source: offline)', async () => {
    stub(() => json({}, false))
    const r = await roadRoute([A, B], FUTURE)
    expect(r).not.toBeNull()
    expect(r!.source).toBe('offline')
    expect(r!.km).toBeGreaterThan(0)
    expect(r!.hours).toBeGreaterThan(0)
  })

  it('dưới 2 điểm thì trả về null', async () => {
    expect(await roadRoute([A], FUTURE)).toBeNull()
  })

  it('cùng đầu vào thì dùng bộ nhớ đệm, không gọi lại mạng', async () => {
    const f = stub(() => json(osrmBody))
    await roadRoute([A, B], FUTURE)
    await roadRoute([A, B], FUTURE + 60_000)
    expect(f).toHaveBeenCalledTimes(1)
  })

  it('lưu tuyến vào sessionStorage để không gọi lại mạng khi chuyển trang', async () => {
    const store = new Map<string, string>()
    const fakeSession = {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => store.set(k, v),
      removeItem: (k: string) => store.delete(k),
    }
    vi.stubGlobal('window', { sessionStorage: fakeSession })

    const f = stub(() => json(osrmBody))
    const r1 = await roadRoute([A, B], FUTURE)
    expect(r1).not.toBeNull()
    expect(f).toHaveBeenCalledTimes(1)

    const k = `${A.lat.toFixed(4)},${A.lng.toFixed(4)};${B.lat.toFixed(4)},${B.lng.toFixed(4)}`
    expect(fakeSession.getItem('RHT_ROAD_' + k)).not.toBeNull()

    // Lần gọi tiếp theo tái sử dụng session cache
    const r2 = await roadRoute([A, B], FUTURE)
    expect(r2!.km).toBe(280)
    expect(f).toHaveBeenCalledTimes(1)
  })
})

describe('ghi chú nguồn thời gian lái', () => {
  it('nói rõ nguồn: đang tính, OSRM, ước lượng', async () => {
    stub(() => json(osrmBody))
    const osrm = await roadRoute([A, B], FUTURE)
    expect(roadNote(undefined, true)).toMatch(/đang tính/)
    expect(roadNote(osrm, false)).toMatch(/OSRM/)
    expect(roadNote({ ...osrm!, source: 'offline' }, false)).toMatch(/ước lượng/)
    expect(roadNote(null, false)).toMatch(/ước lượng/)
  })
})
