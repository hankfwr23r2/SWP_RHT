import { expect, it } from 'vitest'
import { departureOptions, searchRange } from './plans'

it('tìm chuyến trong 8 tuần tới theo ngày Việt Nam', () => {
  expect(searchRange(new Date('2031-03-02T18:00:00Z'))).toEqual({ from: '2031-03-03', to: '2031-04-28' })
})

it('chỉ hiện chuyến khác cùng tuyến còn nhận', () => {
  const rows = [
    { id: 1, routeName: 'A', status: 'OPEN' as const }, { id: 2, routeName: 'A', status: 'OPEN' as const },
    { id: 3, routeName: 'B', status: 'OPEN' as const }, { id: 4, routeName: 'A', status: 'CANCELLED' as const },
  ]
  expect(departureOptions(rows, { departureId: 1, routeName: 'A' }).map(row => row.id)).toEqual([2])
})
