// Sitemap chung của cả 2 app (thay cho sitemap.html cũ): mọi trang, nhóm theo vai trò, bấm để mở.
// Trang cùng app chuyển bằng React Router; trang của app kia mở bằng liên kết thường (2 app build riêng).
// Danh sách trang của app kia tải khi mở sitemap, không nằm trong bundle chính.
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { AppRoute } from '@shared/routing/types'
import { ROLE_LABEL, type Role } from '@shared/types/role'

type App = 'customer' | 'backoffice'
const BASE: Record<App, string> = { customer: '', backoffice: '/backoffice' }
// Tài khoản mẫu để đăng nhập (giả lập: email chứa từ khóa vai trò)
const ACCOUNT: Record<Role, string> = {
  customer: 'email bất kỳ ở trang Đăng nhập khách',
  admin: 'admin@equine.vn', manager: 'manager@equine.vn', specialist: 'specialist@equine.vn', coordinator: 'ops@equine.vn', driver: 'driver@equine.vn', escort: 'escort@equine.vn',
}
const ORDER: (Role | 'public')[] = ['public', 'customer', 'admin', 'manager', 'specialist', 'coordinator', 'driver', 'escort']

interface Entry { app: App; route: AppRoute }

export function SitemapPage({ app }: { app: App }) {
  const [entries, setEntries] = useState<Entry[]>([])
  useEffect(() => {
    Promise.all([import('../customer/routes'), import('../backoffice/routes')]).then(([c, b]) => setEntries([
      ...c.routes.map(route => ({ app: 'customer' as const, route })),
      ...b.routes.map(route => ({ app: 'backoffice' as const, route })),
    ].filter(e => e.route.path !== '/sitemap')))
  }, [])
  const groupOf = (r: AppRoute) => r.roles[0] ?? 'public'
  const url = (e: Entry) => BASE[e.app] + (e.route.example ? e.route.path.replace(':id', e.route.example) : e.route.path)

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>Sitemap</h1>
          <p>{entries.length} trang của app khách (<code>/</code>) và app nội bộ (<code>/backoffice</code>), nhóm theo vai trò. Trang cần đăng nhập sẽ chuyển qua trang Đăng nhập nếu chưa đăng nhập đúng vai trò.</p>
        </div>
        {ORDER.map(group => {
          const list = entries.filter(e => groupOf(e.route) === group)
          if (!list.length) return null
          return (
            <div key={group} className="card table-wrap">
              <div className="card-header">
                <h3>{group === 'public' ? 'Công khai' : ROLE_LABEL[group]}</h3>
                {group !== 'public' && <span className="sub-text">Đăng nhập: {ACCOUNT[group]}</span>}
              </div>
              <table className="data-table">
                <thead><tr><th>Trang</th><th>URL</th><th>App</th></tr></thead>
                <tbody>
                  {list.map(e => (
                    <tr key={e.app + e.route.path}>
                      <td className="font-semibold">{e.app === app
                        ? <Link to={url(e).slice(BASE[app].length) || '/'} className="text-orange">{e.route.title}</Link>
                        : <a href={url(e)} className="text-orange">{e.route.title}</a>}</td>
                      <td><code>{url(e)}</code></td>
                      <td className="text-muted">{e.app === 'customer' ? 'Khách' : 'Nội bộ'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        })}
      </div>
    </div>
  )
}
