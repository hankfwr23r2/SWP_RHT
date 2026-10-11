// Dữ liệu mẫu của Điều phối phải khớp bộ đơn chung
import { describe, expect, it } from 'vitest'
import { tripStatus } from '../trips'
import { seedCrew, seedVehicles } from './fleet'
import { seedOrders } from './orders'
import { seedOtherOrders } from './orders-others'
import { seedTrips } from './trips'

const orders = [...seedOrders(), ...seedOtherOrders()]
const trips = seedTrips()
const vehicles = seedVehicles()
const crew = seedCrew()
const orderOf = (id: string) => orders.find(o => o.id === id)!

describe('chuyến mẫu', () => {
  it('mã chuyến và mã đơn không trùng', () => {
    expect(new Set(trips.map(t => t.id)).size).toBe(trips.length)
    expect(new Set(trips.map(t => t.orderId)).size).toBe(trips.length)
    expect(new Set(orders.map(o => o.id)).size).toBe(orders.length)
  })
  it('chuyến cũ nối đúng trạng thái', () => {
    const status = (id: string) => { const t = trips.find(x => x.id === id)!; return tripStatus(t, orderOf(t.orderId)) }
    expect(status('TR-9021')).toBe('in_transit')
    expect(status('TR-9042')).toBe('in_transit')
    expect(status('TR-9035')).toBe('pending_assessment')
    expect(status('TR-9036')).toBe('pending_assessment')
    expect(status('TR-9050')).toBe('awaiting_routing')
    expect(status('TR-9051')).toBe('awaiting_routing')
    expect(status('TR-9018')).toBe('assigned')
  })
  it('đơn đã chốt lộ trình có đủ xe, tài xế, hộ tống', () => {
    trips.filter(t => ['assigned', 'in_transit'].includes(tripStatus(t, orderOf(t.orderId)) ?? '')).forEach(t => {
      t.legs.forEach(l => {
        const v = vehicles.find(x => x.id === l.vehicleId)
        expect(v, `${t.id} chặng ${l.no}`).toBeTruthy()
        expect(crew.find(c => c.id === l.driverId && c.role === 'driver'), `${t.id} tài xế`).toBeTruthy()
        expect(crew.find(c => c.id === l.escortId && c.role === 'escort'), `${t.id} hộ tống`).toBeTruthy()
      })
    })
  })
  it('tài xế và hộ tống ghi trong review có trong danh sách', () => {
    orders.filter(o => o.review).forEach(o => {
      expect(vehicles.some(v => o.review!.vehicle.startsWith(v.plate)), `${o.id} xe`).toBe(true)
      expect(crew.some(c => c.name === o.review!.driver), `${o.id} tài xế`).toBe(true)
    })
  })
})
