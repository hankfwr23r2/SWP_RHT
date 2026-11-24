// Trang chủ công khai: màn hình đầu hiện ngay, phần bên dưới tải sau
import { lazy, Suspense } from 'react'
import { Hero } from '@shared/components/landing/Hero'
import { Lookup } from './Lookup'
import s from './HomePage.module.css'

const HomeSections = lazy(() => import('./HomeSections'))

export default function HomePage() {
  return (
    <>
      <Hero />
      <Lookup />
      <Suspense fallback={<div className={s.pending} />}><HomeSections /></Suspense>
    </>
  )
}
