import { staffAccountsApi, type StaffAccount, type StaffAccountUpdate } from '@shared/api/staffAccounts'
import { useCatalogAction } from '@backoffice/shared/useCatalogAction'
import { useCatalogForm } from '@backoffice/shared/useCatalogForm'
import { ROLE_LABEL } from '@shared/types/role'

export const staffRoles = Object.keys(ROLE_LABEL).filter(role => role !== 'customer').map(role => role.toUpperCase())

export function useAccountEditor(account: StaffAccount | undefined, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({
    username: account?.username ?? '', email: account?.email ?? '', roleCode: account?.roleCode ?? '',
    staffCode: account?.staffCode ?? '', fullName: account?.fullName ?? '', phone: account?.phone ?? '',
    hireDate: account?.hireDate ?? '', homeHubLocationId: String(account?.homeHubLocationId ?? ''),
    driverLicenseClass: account?.driverLicenseClass ?? '', driverLicenseExpiryDate: account?.driverLicenseExpiryDate ?? '',
  })
  const action = useCatalogAction(reload)
  async function save() {
    const { values } = form
    const profile: StaffAccountUpdate = {
      staffCode: values.staffCode.trim(),
      fullName: values.fullName.trim(),
      phone: values.phone.trim(),
      hireDate: values.hireDate,
      homeHubLocationId: Number(values.homeHubLocationId),
      ...(values.roleCode === 'DRIVER' ? {
        driverLicenseClass: values.driverLicenseClass.trim() || undefined,
        driverLicenseExpiryDate: values.driverLicenseExpiryDate || undefined,
      } : {}),
    }
    const id = account?.userId
    if (await action.run(() => id != null ? staffAccountsApi.update(id, profile) : staffAccountsApi.create({
      ...profile, username: values.username.trim(), email: values.email.trim(), password: values.password, roleCode: values.roleCode,
    }))) close()
  }
  return { ...form, ...action, save }
}

export type AccountAction = 'lock' | 'unlock'

export function useAccountStatus(id: number, event: AccountAction, reload: () => unknown, close: () => void) {
  const form = useCatalogForm({ reason: '' })
  const action = useCatalogAction(reload)
  async function save() {
    if (await action.run(() => staffAccountsApi[event](id, form.values.reason.trim()))) close()
  }
  return { ...form, ...action, save }
}
