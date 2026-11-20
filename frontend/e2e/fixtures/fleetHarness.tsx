import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import FleetPage from '../../src/apps/backoffice/features/coordinator/fleet/FleetPage'
import '../../src/shared/styles/base.css'
const manage = new URLSearchParams(location.search).has('manage')
createRoot(document.getElementById('root')!).render(<RouterProvider router={createBrowserRouter([
  { path: '/fleet-test', element: <FleetPage manage={manage} /> },
  { path: '/coordinator/fleet-plan/:id', element: <p>Trang đơn</p> },
])} />)
