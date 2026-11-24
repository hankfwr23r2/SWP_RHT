import { expect, it } from 'vitest'
import { filterAccounts } from './filterAccounts'

it('lọc đồng thời theo nội dung tìm kiếm, vai trò và trạng thái', () => {
  const accounts = [
    { username: 'driver.an', roleCode: 'DRIVER', accountStatus: 'LOCKED' as const },
    { username: 'driver.binh', roleCode: 'DRIVER', accountStatus: 'ACTIVE' as const },
    { username: 'admin.an', roleCode: 'ADMIN', accountStatus: 'LOCKED' as const },
  ]
  expect(filterAccounts(accounts, '  AN ', 'DRIVER', 'LOCKED')).toEqual([accounts[0]])
  expect(filterAccounts(accounts, '', '', '')).toEqual(accounts)
})

it('tìm theo mã nhân viên khi hồ sơ thiếu tên', () => {
  const account = { staffCode: 'NV-100' }
  expect(filterAccounts([account], 'nv-100', '', '')).toEqual([account])
})
