import { useEffect, type ComponentType, type ReactNode } from 'react'
import { createBrowserRouter, Outlet } from 'react-router'
import { RequireRole } from '../auth/RequireRole'
import type { AppRoute } from './types'

type Layouts = Partial<Record<NonNullable<AppRoute['layout']>, ComponentType<{ children: ReactNode }>>>

function Titled({ title, children }: { title: string; children: ReactNode }) {
  useEffect(() => { document.title = `${title} — Vận chuyển Ngựa đua` }, [title])
  return children
}

// Dựng router từ danh sách trang: bọc layout, chặn vai trò, đặt tiêu đề tab.
export function buildRouter(routes: AppRoute[], layouts: Layouts, opts: { basename?: string; loginPath: string }) {
  return createBrowserRouter(
    [{
      element: <Outlet />,
      children: routes.map(r => {
        const Page = r.page
        const Layout = (r.layout && layouts[r.layout]) || (({ children }: { children: ReactNode }) => children)
        let element = <Titled title={r.title}><Layout><Page /></Layout></Titled>
        if (r.roles.length) element = <RequireRole roles={r.roles} loginPath={opts.loginPath}>{element}</RequireRole>
        return { path: r.path, element }
      }),
    }],
    { basename: opts.basename },
  )
}
