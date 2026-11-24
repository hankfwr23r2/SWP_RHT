import { useTranslation } from 'react-i18next'
import { useLoad } from '@shared/hooks/useLoad'
import { loadAll } from '@shared/routing/pageData'
import { customerAddressesApi, type CustomerAddress, type CustomerAddressCreate } from '@shared/api/customerAddresses'
import { catalogApi } from '@shared/api/catalog'
import { useToast } from '@shared/ui/toast'

export async function addressesLoader() {
  return { errors: [], result: loadAll({ addresses: customerAddressesApi.list() }) }
}

export function useAddressCountries() {
  return useLoad(catalogApi.countries, [], { toastOnError: false })
}

export function useAddressProvinces(country: string) {
  const state = useLoad(
    async () => ({ country, items: country ? await catalogApi.provinces(country) : [] }),
    [country],
    { toastOnError: false },
  )
  return { ...state, provinces: state.data?.country === country ? state.data.items : undefined }
}

export function useAddressLabels(addresses: CustomerAddress[]) {
  const codes = [...new Set(addresses.flatMap(address => address.countryCode ? [address.countryCode] : []))].sort().join(',')
  return useLoad(async () => {
    if (!codes) return undefined
    const [countries, ...provinces] = await Promise.allSettled([
      catalogApi.countries(),
      ...codes.split(',').map(country => catalogApi.provinces(country)),
    ] as const)
    return {
      countries: countries.status === 'fulfilled' ? countries.value : [],
      provinces: provinces.flatMap(result => result.status === 'fulfilled' ? result.value : []),
    }
  }, [codes], { toastOnError: false }).data
}

export function useAddressActions() {
  const { t } = useTranslation()
  const toast = useToast()
  return {
    async save(id: number | undefined, body: CustomerAddressCreate) {
      try {
        if (id == null) await customerAddressesApi.create(body)
        else await customerAddressesApi.update(id, body)
        toast(t('addresses.saved'))
      } catch (error) {
        toast(t('addresses.saveError'), 'error')
        throw error
      }
    },
    async remove(id: number) {
      try {
        await customerAddressesApi.remove(id)
        toast(t('addresses.deleted'))
      } catch (error) {
        toast(t('addresses.deleteError'), 'error')
        throw error
      }
    },
  }
}
