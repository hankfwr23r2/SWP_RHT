// Dữ liệu trang do router tải (loader): router gọi song song với lúc tải file trang, trang hiện ra là đã có dữ liệu.
import { useEffect } from 'react'
import { useLoaderData, useRevalidator, type Params, type ShouldRevalidateFunction } from 'react-router'
import { readSession } from '../auth/session'
import type { Role, Session } from '../types/role'
import { useToast } from '../ui/toast'

export interface PageLoaderContext { params: Params; session: Session }
export type PageLoader = (ctx: PageLoaderContext) => Promise<{ errors: string[] }>

type Loaded<T> = { [K in keyof T]: Awaited<T[K]> | undefined } & { errors: string[] }

// Chờ song song các nguồn dữ liệu của một trang; nguồn nào lỗi thì để undefined và gom thông báo lỗi để trang báo
export async function loadAll<T extends Record<string, Promise<unknown>>>(sources: T): Promise<Loaded<T>> {
  const keys = Object.keys(sources)
  const results = await Promise.allSettled(Object.values(sources))
  const out: Record<string, unknown> = {}
  const errors: string[] = []
  results.forEach((r, i) => {
    out[keys[i]] = r.status === 'fulfilled' ? r.value : undefined
    if (r.status === 'rejected') errors.push(r.reason instanceof Error ? r.reason.message : 'Không thể tải dữ liệu.')
  })
  return { ...out, errors } as Loaded<T>
}

// Loader chạy trước RequireRole: chưa đăng nhập đúng vai trò thì không gọi API, để RequireRole chuyển về trang đăng nhập
export function guardLoader(loader: PageLoader, roles: Role[], app: string) {
  return ({ params }: { params: Params }) => {
    const session = readSession(app)
    return session && roles.includes(session.role) ? loader({ params, session }) : null
  }
}

// Chỉ đổi query trên cùng trang (?group=..., ?tab=...) thì không tải lại: loader không đọc query, trang tự lọc tại chỗ.
// reload() (cùng URL) và chuyển sang trang khác vẫn tải lại như mặc định.
export const keepOnQueryChange: ShouldRevalidateFunction = ({ currentUrl, nextUrl, defaultShouldRevalidate }) =>
  currentUrl.pathname === nextUrl.pathname && currentUrl.search !== nextUrl.search ? false : defaultShouldRevalidate

// Đọc dữ liệu loader của trang. Lỗi tải báo bằng toast như useLoad; reload() tải lại ngầm (giữ dữ liệu cũ tới khi có dữ liệu mới).
export function usePageData<L extends PageLoader>() {
  const data = useLoaderData() as Awaited<ReturnType<L>>
  const { revalidate } = useRevalidator()
  const toast = useToast()
  useEffect(() => { for (const error of data.errors) toast(error, 'error') }, [data, toast])
  return { ...data, reload: revalidate }
}
