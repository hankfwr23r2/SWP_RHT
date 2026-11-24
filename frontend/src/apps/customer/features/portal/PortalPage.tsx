// Trang chủ của khách sau đăng nhập: banner, số liệu nhanh, việc cần làm, các bước của một đơn, giá tạm tính, mạng lưới và dịch vụ.
import { Suspense } from 'react'
import { Await, Link, useLoaderData } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { Hero } from '@shared/components/landing/Hero'
import { portalLinks, useHashScroll } from '@shared/components/landing/landingMotion'
import { Network } from '@shared/components/landing/Network'
import { PricePrompt } from '@shared/components/landing/PricePrompt'
import { Services } from '@shared/components/landing/Services'
import { useLandingReveal } from '@shared/components/landing/useLandingReveal'
import { useStaggerIn } from '@shared/motion/motion'
import { Card } from '@shared/ui/Card'
import type { portalLoader } from './loaders'
import { PortalSummary, PortalSummaryLoading } from './PortalSummary'
import s from './PortalPage.module.css'

const STEPS = ['book', 'review', 'quote', 'deposit', 'prepare', 'pickup', 'transport', 'deliver']

export default function PortalPage() {
  const { t } = useTranslation()
  const { session } = useAuth()
  const { result } = useLoaderData() as Awaited<ReturnType<typeof portalLoader>>
  const reveal = useLandingReveal()
  // Chỉ chạy hiệu ứng một lần lúc vào trang (trang sẽ giật nếu chạy lại)
  const stagger = useStaggerIn('[data-card]')
  useHashScroll()

  return (
    <div ref={reveal}>
      <Hero link={portalLinks} />
      <div ref={stagger} className="page">
        <div className={`wrap ${s.stack}`}>
          <section className={s.greet} data-card>
            <span className={s.hello}><i className="fa-solid fa-hand" aria-hidden="true" /> {t('portal.hello', { name: session?.name })}</span>
            <div className={s.actions}>
              <Link to="/booking/route" className="btn btn-primary"><i className="fa-solid fa-plus" aria-hidden="true" /> {t('common.bookNewTrip')}</Link>
              <Link to="/horses" className="btn btn-ghost"><i className="fa-solid fa-horse-head" aria-hidden="true" /> {t('nav.horses')}</Link>
            </div>
          </section>

          <div className={s.stack} data-card>
            <Suspense fallback={<PortalSummaryLoading />}>
              <Await resolve={result}>{data => <PortalSummary data={data} />}</Await>
            </Suspense>
          </div>

          <div className={s.anchor} data-card id="quy-trinh">
            <Card title={t('portal.workflowTitle')}>
              <ol className={s.steps}>
                {STEPS.map((step, i) => (
                  <li key={step}>
                    <span className={s.stepNum} aria-hidden="true">{i + 1}</span>
                    <div><b>{t(`portal.steps.${step}.title`)}</b><p>{t(`portal.steps.${step}.text`)}</p></div>
                  </li>
                ))}
              </ol>
            </Card>
          </div>

          <div data-card><Card><PricePrompt /></Card></div>
        </div>
      </div>
      <Network />
      <Services link={portalLinks} />
    </div>
  )
}
