import type { StaffAccount } from '@shared/api/staffAccounts'

export function filterAccounts(accounts: StaffAccount[], search: string, role: string, status: string) {
  const query = search.trim().toLowerCase()
  return accounts.filter(account =>
    (!role || account.roleCode === role)
    && (!status || account.accountStatus === status)
    && [account.fullName, account.username, account.email, account.staffCode]
      .some(value => (value ?? '').toLowerCase().includes(query)),
  )
}
