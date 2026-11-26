import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import type { Horse, HorseDocument, HorseType } from '@shared/api/horses'
import { Button } from '@shared/ui/Button'
import { ErrorState } from '@shared/ui/ErrorState'
import { Field } from '@shared/ui/Field'
import { Input } from '@shared/ui/Input'
import { Modal } from '@shared/ui/Modal'
import { Select } from '@shared/ui/Select'
import { Skeleton } from '@shared/ui/Skeleton'
import { Tabs } from '@shared/ui/Tabs'
import { Textarea } from '@shared/ui/Textarea'
import { HorseDocumentFields } from './HorseDocumentFields'
import { currentHorseYear, MIN_BIRTH_YEAR } from './horseProfileDisplay'
import { useHorseFormData } from './loaders'
import { HORSE_SEXES, useHorseForm, type HorseFormTab } from './useHorseForm'
import s from './Horses.module.css'

type ModalProps = { onClose: () => void; onSaved: (complete: boolean) => void; initialTab?: HorseFormTab }

export function HorseProfileForm({ id, onClose, onSaved, initialTab = 'info' }: ModalProps & { id?: number }) {
  const { t } = useTranslation()
  const { data, loaded, error, reload } = useHorseFormData(id)
  if (data && !error) return <ProfileFields {...data} initialTab={initialTab} onClose={onClose} onSaved={onSaved} />
  return (
    <Modal wide title={t(id == null ? 'horses.addHorse' : 'horses.editProfile')} onClose={onClose}>
      {!loaded ? <Skeleton lines={5} /> : <ErrorState onRetry={reload} />}
    </Modal>
  )
}

function ProfileFields({ horse, types, documents, initialTab = 'info', onClose, onSaved }:
  ModalProps & { horse?: Horse; types: HorseType[]; documents: HorseDocument[] }) {
  const { t } = useTranslation()
  const formId = useId()
  const profile = useHorseForm(horse, documents, initialTab, onSaved)
  const { profileId, tab, setTab, values, set, errors, busy, selected, partial, submit } = profile
  const editing = profileId != null
  const onInfo = tab === 'info'
  const documentCount = profile.documents.filter(document => {
    const choice = selected[document.documentTypeCode ?? '']
    return document.canUpload === false || (choice != null && !choice.error)
  }).length
  const cancel = () => { if (!profile.saving.current) onClose() }

  return (
    <Modal wide title={t(editing ? 'horses.editProfile' : 'horses.addHorse')} onClose={cancel} footer={
      <>
        <Button variant="ghost" onClick={cancel} disabled={busy}>{t('ui.cancel')}</Button>
        <Button onClick={() => setTab(onInfo ? 'documents' : 'info')} disabled={busy}>
          {t(onInfo ? 'horses.continueToDocuments' : 'common.back')}
        </Button>
        <Button variant="primary" type="submit" form={formId} loading={busy}>
          {t(partial && !onInfo ? 'horses.retryDocuments' : 'common.save')}
        </Button>
      </>
    }>
      <form id={formId} className={s.form} onSubmit={submit} noValidate>
        <Tabs label={t('horses.profileTabs')} value={tab} onChange={value => { if (!busy) setTab(value === 'info' ? 'info' : 'documents') }}
          items={[
            { value: 'info', label: t('horses.identity') },
            { value: 'documents', label: t('horses.documentsTab', { done: documentCount, total: profile.documents.length }) },
          ]}>
          {onInfo ? <>
            <div className={s.grid2}>
              <Field label={t('horses.name')} error={errors.name} required>
                {control => <Input {...control} data-autofocus value={values.name} maxLength={100} disabled={busy}
                  onChange={e => set('name', e.target.value)} />}
              </Field>
              <Field label={t('horses.microchip')} hint={t('horses.chipHint')} error={errors.microchip} required>
                {control => <Input {...control} value={values.microchip} disabled={busy || editing} inputMode="numeric"
                  maxLength={30} onChange={e => set('microchip', e.target.value)} />}
              </Field>
              <Field label={t('horses.horseTypeId')} error={errors.horseTypeId} required>
                {control => <Select {...control} value={values.horseTypeId} onChange={value => set('horseTypeId', value)}
                  placeholder={t('horses.chooseType')} disabled={busy}
                  options={types.flatMap(type => type.id == null || !type.name ? [] : [{ value: String(type.id), label: type.name }])} />}
              </Field>
              <Field label={t('horses.gender')} error={errors.sex} required>
                {control => <Select {...control} value={values.sex} onChange={value => set('sex', value)} disabled={busy}
                  placeholder={t('horses.chooseGender')}
                  options={HORSE_SEXES.map(sex => ({ value: sex, label: t(`horses.genderLabel.${sex.toLowerCase()}`) }))} />}
              </Field>
              <Field label={t('horses.color')} error={errors.color} required>
                {control => <Input {...control} value={values.color} maxLength={50} disabled={busy} onChange={e => set('color', e.target.value)} />}
              </Field>
              <Field label={t('horses.birthYear')} error={errors.birthYear} required>
                {control => <Input {...control} type="number" min={MIN_BIRTH_YEAR} max={currentHorseYear()} step={1}
                  value={values.birthYear} disabled={busy} onChange={e => set('birthYear', e.target.value)} />}
              </Field>
            </div>
            <Field label={t('horses.identificationMarks')} error={errors.identificationMarks} className={s.identification}>
              {control => <Textarea {...control} value={values.identificationMarks} maxLength={2000} disabled={busy}
                onChange={e => set('identificationMarks', e.target.value)} />}
            </Field>
          </> : <>
            {partial && <p className={s.notice} role="alert">{t('horses.partialSave')}</p>}
            <HorseDocumentFields documents={profile.documents} selected={selected} onSelect={profile.selectFile} busy={busy} />
          </>}
        </Tabs>
      </form>
    </Modal>
  )
}
