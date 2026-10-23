import { Suspense, useEffect, type ComponentType, type ReactNode } from 'react'
import { createBrowserRouter, Outlet } from 'react-router'
import { RequireRole } from '../auth/RequireRole'
import { NavigationBar, NavigationProgress } from './NavigationProgress'
import type { AppRoute } from './types'

type Layouts = Partial<Record<NonNullable<AppRoute['layout']>, ComponentType<{ children: ReactNode }>>>

function Titled({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => { document.title = `${title} — Vận chuyển Ngựa đua` }, [title])
  return children
}

// Dựng router từ danh sách trang: bọc layout, chặn vai trò, đặt tiêu đề tab.
export function buildRouter(routes: AppRoute[], layouts: Layouts, opts: { basename?: string; loginPath: string }) {
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
      children: routes.map(r => r.lazyPage
        ? { path: r.path, loader: r.loader, lazy: async () => ({ element: wrap(r, (await r.lazyPage!()).default) }) }
        : { path: r.path, loader: r.loader, element: wrap(r, r.page!) }),
    }],
    { basename: opts.basename },
  )
}
