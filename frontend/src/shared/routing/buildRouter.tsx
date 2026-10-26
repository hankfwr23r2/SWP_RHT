import { Suspense, useEffect, type ComponentType, type ReactNode } from 'react'
import { createBrowserRouter, Outlet, type Params } from 'react-router'
import { RequireRole } from '../auth/RequireRole'
import { readSession } from '../auth/session'
import { loadBootstrap } from '../config/bootstrap'
import { NavigationBar, NavigationProgress } from './NavigationProgress'
import { guardLoader, keepOnQueryChange } from './pageData'
import { RouteError } from './RouteError'
import type { AppRoute } from './types'

type Layouts = Partial<Record<NonNullable<AppRoute['layout']>, ComponentType<{ children: ReactNode }>>>

function Titled({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => { document.title = `${title} — Vận chuyển Ngựa đua` }, [title])
  return children
}

// Dựng router từ danh sách trang: bọc layout, chặn vai trò, đặt tiêu đề tab.
export function buildRouter(routes: AppRoute[], layouts: Layouts, opts: { app: string; basename?: string; loginPath: string }) {
  const wrap = (r: AppRoute, Page: ComponentType) => {
    const Layout = (r.layout && layouts[r.layout]) || (({ children }: { children: ReactNode }) => children)
    // Suspense nằm trong layout: khi trang lazy đang tải, menu/khung vẫn đứng yên, chỉ phần nội dung chờ
    let element = <Titled title={r.title}><Layout><Suspense fallback={null}><Page /></Suspense></Layout></Titled>
    if (r.roles.length) element = <RequireRole roles={r.roles} loginPath={opts.loginPath}>{element}</RequireRole>
    return element
  }
  return createBrowserRouter(
    [{
      element: <><NavigationProgress /><Outlet /></>,
      // Mở thẳng URL của trang có loader (F5): chưa có gì để hiện tới khi dữ liệu về
      HydrateFallback: NavigationBar,
      errorElement: <RouteError />,
      children: routes.map(r => {
        // Trang dùng danh mục chờ dữ liệu khởi động trước rồi mới tải dữ liệu riêng của trang.
        // Chưa đăng nhập đúng vai trò thì không chờ, để RequireRole chuyển ngay về trang đăng nhập.
        const pageLoader = r.loader && guardLoader(r.loader, r.roles, opts.app)
        const loader = r.catalog === false
          ? pageLoader
          : async (args: { params: Params }) => {
            const session = readSession(opts.app)
            if (!r.roles.length || (session && r.roles.includes(session.role))) await loadBootstrap()
            return pageLoader ? pageLoader(args) : null
          }
        const data = loader && { loader, shouldRevalidate: keepOnQueryChange }
        return r.lazyPage
          ? { path: r.path, ...data, lazy: async () => ({ element: wrap(r, (await r.lazyPage!()).default) }) }
          : { path: r.path, ...data, element: wrap(r, r.page!) }
      }),
    }],
    { basename: opts.basename },
  )
}
