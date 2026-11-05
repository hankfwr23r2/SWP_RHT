import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider, useLoaderData, useRevalidator } from 'react-router'
import { InsurancePanel } from '../../src/apps/backoffice/shared/InsurancePanel'
import { toBooking } from '../../src/shared/services/bookingMapper'
import type { BackendBookingResponse } from '../../src/shared/api/bookings'
import '../../src/shared/styles/base.css'

function Panel() {
  const booking = useLoaderData() as BackendBookingResponse
  const { revalidate } = useRevalidator()
  return <InsurancePanel b={toBooking(booking)} onDone={revalidate} />
}

createRoot(document.getElementById('root')!).render(<RouterProvider router={createBrowserRouter([{
  path: '/insurance-lock-test', element: <Panel />,
  loader: async () => (await fetch('/api/v1/fixture/insurance-booking')).json(),
}])} />)
