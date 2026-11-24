// Hồ sơ khách: thông tin theo GET/PUT /customers/me và đổi mật khẩu
import { Suspense, useState } from 'react'
import { Await, Link, useLoaderData, useRevalidator } from 'react-router'
import { useTranslation } from 'react-i18next'
import type { CustomerProfile } from '@shared/api/bookingOrders'
import type { Country } from '@shared/api/catalog'
import { Card } from '@shared/ui/Card'
import { ErrorState } from '@shared/ui/ErrorState'
import { Skeleton } from '@shared/ui/Skeleton'
import { Tabs } from '@shared/ui/Tabs'
import { InfoForm } from './InfoForm'
import type { profileLoader } from './loaders'
import { PasswordForm } from './PasswordForm'
import s from './Profile.module.css'

const initialsOf = (name: string) =>
  name.split(' ').filter(Boolean).slice(-2).map(word => word[0]).join('').toUpperCase()

export default function ProfilePage() {
  const { t } = useTranslation()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof profileLoader>>
  const { revalidate, state } = useRevalidator()
  return (
    <div className="page"><div className={`wrap ${s.page}`}>
      <div className="breadcrumb"><Link to="/portal">{t('nav.home')}</Link> / <span>{t('profile.title')}</span></div>
      <Suspense fallback={<><div className={s.hero}><h1>{t('profile.title')}</h1></div><Card><Skeleton lines={8} /></Card></>}>
        <Await resolve={result}>
          {({ profile, countries }) => profile
            ? <Profile profile={profile} countries={countries} />
            : <><div className={s.hero}><h1>{t('profile.title')}</h1></div><ErrorState onRetry={revalidate} retrying={state !== 'idle'} /></>}
        </Await>
      </Suspense>
    </div></div>
  )
}

function Profile({ profile, countries }: { profile: CustomerProfile; countries?: Country[] }) {
  const { t } = useTranslation()
  const [tab, setTab] = useState('info')
  const name = profile.fullName || t('profile.customerRole')
  return (
    <>
      <header className={s.hero}>
        <div className={s.avatar} aria-hidden="true">{initialsOf(name)}</div>
        <div>
          <h1>{name}</h1>
          <p><span>{profile.email}</span> <span className={s.role}>{t('profile.customerRole')}</span></p>
        </div>
      </header>
      <Tabs label={t('profile.title')} value={tab} onChange={setTab}
        items={[{ value: 'info', label: t('profile.tabInfo') }, { value: 'security', label: t('profile.tabSecurity') }]}>
        {/* Giữ cả hai biểu mẫu để đổi tab không mất nội dung đang nhập */}
        <div hidden={tab !== 'info'}><InfoForm profile={profile} countries={countries} /></div>
        <div hidden={tab !== 'security'}><PasswordForm /></div>
      </Tabs>
    </>
  )
}
