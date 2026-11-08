import { expect, it, vi } from 'vitest'
import { adminAccountsApi } from '../api/adminAccounts'
import { accountsApi } from './accounts'

vi.mock('../api/adminAccounts', () => ({ adminAccountsApi: { list: vi.fn() } }))

it('retains backend audit history when mapping accounts for the admin modal', async () => {
  vi.mocked(adminAccountsApi.list).mockResolvedValue([{
    id: 'TK-007', userId: 7, name: 'Staff', email: 'staff@example.com', role: 'driver', status: 'active',
    history: [{ id: 42, actionType: 'LOCK_ACCOUNT', actionByName: 'Admin', actionByEmail: 'admin@example.com', targetUserId: 7, description: 'Khóa tài khoản: Nghỉ việc', createdAt: '2026-11-08T05:00:00Z' }],
  }])
  const [account] = await accountsApi.list()
  expect(account.history).toEqual([{ time: Date.parse('2026-11-08T05:00:00Z'), actor: 'Admin', text: 'Khóa tài khoản: Nghỉ việc' }])
})
