import { describe, expect, it } from 'vitest'
import { crewStep } from './crewStep'

// Bước của một thành viên kíp xe (tài xế hoặc hộ tống) trên app, trước khi xe lăn bánh:
// - acknowledge: chưa bấm "Tôi đã nhận lệnh" → luôn hiện nút nhận lệnh, kể cả khi giấy tờ đã xong
// - waiting_documents: đã nhận lệnh, Chuyên viên chưa hoàn tất giấy tờ
// - waiting_crew: đã nhận lệnh, giấy tờ đã xong, còn chờ người kia trong kíp nhận lệnh (đơn chưa sẵn sàng)
// - ready: đơn đã sẵn sàng đón ngựa (tài xế được bấm xuất phát)
// - departed: xe đã xuất phát
const NOW = 1_700_000_000_000

describe('bước của kíp xe trước khi xuất phát', () => {
  it('chưa nhận lệnh thì phải nhận lệnh, dù giấy tờ đã xong', () => {
    expect(crewStep({ status: 'clearance_in_progress' }, { acks: {} }, 'driver')).toBe('acknowledge')
    expect(crewStep({ status: 'clearance_done', clearance: { doneAt: NOW } }, { acks: { driver: NOW } }, 'escort')).toBe('acknowledge')
  })
  it('đã nhận lệnh, giấy tờ chưa xong thì chờ giấy tờ', () => {
    expect(crewStep({ status: 'clearance_in_progress' }, { acks: { driver: NOW } }, 'driver')).toBe('waiting_documents')
  })
  it('đã nhận lệnh, giấy tờ xong nhưng đơn chưa sẵn sàng thì chờ người kia trong kíp', () => {
    expect(crewStep({ status: 'clearance_done', clearance: { doneAt: NOW } }, { acks: { driver: NOW } }, 'driver')).toBe('waiting_crew')
  })
  it('đơn sẵn sàng đón ngựa thì sẵn sàng', () => {
    expect(crewStep({ status: 'ready_for_pickup', clearance: { doneAt: NOW } }, { acks: { driver: NOW, escort: NOW } }, 'driver')).toBe('ready')
    expect(crewStep({ status: 'ready_for_pickup', clearance: { doneAt: NOW } }, { acks: { driver: NOW, escort: NOW } }, 'escort')).toBe('ready')
  })
  it('xe đã xuất phát thì không còn ở các bước chuẩn bị', () => {
    expect(crewStep({ status: 'en_route_to_pickup', clearance: { doneAt: NOW } }, { acks: { driver: NOW, escort: NOW }, departedAt: NOW }, 'driver')).toBe('departed')
  })
})
