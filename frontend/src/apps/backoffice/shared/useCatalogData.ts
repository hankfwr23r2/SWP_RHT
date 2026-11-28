
import { useLoaderData, useRevalidator } from 'react-router'
import { useLoad } from '@shared/hooks/useLoad'
import { loadAll } from '@shared/routing/pageData'

// Khởi chạy yêu cầu trong loader để trang có thể hiện khung trong lúc chờ API.
export async function loadCatalog<T extends Record<string, Promise<unknown>>>(sources: T) {
  return { errors: [] as string[], result: loadAll(sources) }
}

export function useCatalogData<L extends () => Promise<{ result: Promise<{ errors: string[] }> }>>() {
  const { result } = useLoaderData() as Awaited<ReturnType<L>>
  const request = useLoad(() => result, [result], { toastOnError: false })
  const { revalidate, state } = useRevalidator()
  return {
    data: request.data as Awaited<Awaited<ReturnType<L>>['result']> | undefined,
    loading: !request.data && !request.loaded,
    failed: Boolean(request.error || request.data?.errors.length),
    reload: revalidate,
    retrying: state !== 'idle' || !request.loaded,
  }
}
