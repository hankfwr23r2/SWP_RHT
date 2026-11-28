// Hồ sơ khách: thông tin theo GET/PUT /customers/me và đổi mật khẩu
import { Suspense, useState } from 'react'
import { Await, Link, useLoaderData, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { CustomerProfile } from '@shared/api/bookingOrders'
import type { Country } from '@shared/api/catalog'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { ProfileHeader } from '@shared/ui/ProfileHeader'
import { Skeleton } from '@shared/ui/Skeleton'
import { Tabs } from '@shared/ui/Tabs'
import { InfoForm } from './InfoForm'
import type { profileLoader } from './loaders'
import { PasswordForm } from './PasswordForm'
import s from './Profile.module.css'

export default function ProfilePage() {
  const { t } = useTranslation()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof profileLoader>>
  const { revalidate, state } = useRevalidator()
  return (
    <div className="page"><div className={`wrap ${s.page}`}>
      <div className="breadcrumb"><Link to="/portal">{t('nav.home')}</Link> / <span>{t('profile.title')}</span></div>
      <Suspense fallback={<><ProfileHeader name={t('profile.title')} /><Card><Skeleton lines={8} /></Card></>}>
        <Await resolve={result}>
          {({ profile, countries, horses, orders }) => profile
            ? <Profile profile={profile} countries={countries} horseCount={horses?.length} orderCount={orders?.length} />
            : <><ProfileHeader name={t('profile.title')} /><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></>}
        </Await>
      </Suspense>
    </div></div>
  )
}

function Profile({ profile, countries, horseCount, orderCount }: {
  profile: CustomerProfile; countries?: Country[]; horseCount?: number; orderCount?: number
}) {
  const { t } = useTranslation()
  const [tab, setTab] = useState('info')
  const name = profile.fullName || t('profile.customerRole')
  return (
    <>
      <ProfileHeader name={name} detail={profile.email} role={t('profile.customerRole')} facts={[
        { label: t('nav.horses'), value: horseCount ?? '—' },
        { label: t('myOrders.title'), value: orderCount ?? '—' },
      ]} />
      <Tabs label={t('profile.title')} value={tab} onChange={setTab}
        items={[{ value: 'info', label: t('profile.tabInfo') }, { value: 'security', label: t('profile.tabSecurity') }]}>
        {/* Giữ cả hai biểu mẫu để đổi tab không mất nội dung đang nhập */}
        <div hidden={tab !== 'info'}><InfoForm profile={profile} countries={countries} /></div>
        <div hidden={tab !== 'security'}><PasswordForm /></div>
      </Tabs>
    </>
  )
}
