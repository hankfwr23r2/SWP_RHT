import { customerAddressesApi } from '@shared/api/customerAddresses'
import { loadAll } from '@shared/routing/pageData'

export async function addressesLoader() {
  return { errors: [], result: loadAll({ addresses: customerAddressesApi.list() }) }
}
