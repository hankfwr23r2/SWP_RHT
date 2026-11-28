import { Suspense } from 'react'
import { Await, Link, useLoaderData } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { PageHeader } from '@shared/ui/PageHeader'
import type { portalLoader } from './loaders'
import { PortalSummary, PortalSummaryLoading } from './PortalSummary'
import s from './PortalPage.module.css'

export default function PortalPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof portalLoader>>

  return (
    <div className="page">
      <div className={`wrap ${s.stack}`}>
        <PageHeader title={t('portal.hello', { name: session?.name })} actions={<>
          <Link to="/booking/route" className="btn btn-primary">
            <i className="fa-solid fa-plus" aria-hidden="true" /> {t('common.bookNewTrip')}
          </Link>
          <Link to="/horses" className="btn btn-outline">
            <i className="fa-solid fa-horse-head" aria-hidden="true" /> {t('nav.horses')}
          </Link>
        </>} />
        <Suspense fallback={<PortalSummaryLoading />}>
          <Await resolve={result}>{data => <PortalSummary data={data} />}</Await>
        </Suspense>
      </div>
    </div>
  )
}
