import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { usePageData } from '@shared/routing/pageData'
import { backendHorseApi, type BackendHorseTypeDto } from '@shared/api/horses'
import { useToast } from '@shared/ui/toast'
import type { adminHorseTypesLoader } from '../../../shared/loaders'
import s from './AdminHorseTypes.module.css'

const LEGACY_INSURED_VALUE = 400_000_000

export default function AdminHorseTypesPage() {
  const { t } = useTranslation()
  const initialData = usePageData<typeof adminHorseTypesLoader>()
  const toast = useToast()
  const [horseTypesList, setHorseTypesList] = useState<BackendHorseTypeDto[]>(initialData.horseTypes ?? [])
  const [breedSearch, setBreedSearch] = useState('')
  const [modalMode, setModalMode] = useState<'create' | 'edit' | null>(null)
  const [selectedBreed, setSelectedBreed] = useState<BackendHorseTypeDto | null>(null)
  const [breedName, setBreedName] = useState('')
  const [breedDescription, setBreedDescription] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  const filteredBreeds = useMemo(() => {
    const query = breedSearch.trim().toLowerCase()
    if (!query) return horseTypesList
    return horseTypesList.filter(breed =>
      breed.typeName.toLowerCase().includes(query)
      || (breed.description && breed.description.toLowerCase().includes(query)),
    )
  }, [horseTypesList, breedSearch])

  const openCreateModal = () => {
    setSelectedBreed(null)
    setBreedName('')
    setBreedDescription('')
    setModalMode('create')
  }

  const openEditModal = (breed: BackendHorseTypeDto) => {
    setSelectedBreed(breed)
    setBreedName(breed.typeName)
    setBreedDescription(breed.description ?? '')
    setModalMode('edit')
  }

  const saveBreed = async () => {
    const name = breedName.trim()
    if (!name) {
      toast(t('adminHorseTypes.nameRequired'), 'error')
      return
    }

    setIsSaving(true)
    try {
      if (modalMode === 'create') {
        const created = await backendHorseApi.createType({
          typeName: name,
          description: breedDescription.trim(),
          insuredValue: LEGACY_INSURED_VALUE,
        })
        const nextList = [...horseTypesList, created]
        setHorseTypesList(nextList)
        toast(t('adminHorseTypes.createSuccess', { name: created.typeName }), 'success')
      } else if (modalMode === 'edit' && selectedBreed) {
        const updated = await backendHorseApi.updateType(selectedBreed.id, {
          typeName: name,
          description: breedDescription.trim(),
          insuredValue: selectedBreed.insuredValue ?? LEGACY_INSURED_VALUE,
        })
        const nextList = horseTypesList.map(breed => breed.id === updated.id ? updated : breed)
        setHorseTypesList(nextList)
        toast(t('adminHorseTypes.updateSuccess', { name: updated.typeName }), 'success')
      }

      setModalMode(null)
      setSelectedBreed(null)
    } catch (error) {
      toast(error instanceof Error ? error.message : t('adminHorseTypes.saveError'), 'error')
    } finally {
      setIsSaving(false)
    }
  }

  const deleteBreed = async (breed: BackendHorseTypeDto) => {
    if (!window.confirm(t('adminHorseTypes.deleteConfirm', { name: breed.typeName }))) return

    try {
      await backendHorseApi.deleteType(breed.id)
      const nextList = horseTypesList.filter(item => item.id !== breed.id)
      setHorseTypesList(nextList)
      toast(t('adminHorseTypes.deleteSuccess', { name: breed.typeName }), 'success')
    } catch (error) {
      toast(error instanceof Error ? error.message : t('adminHorseTypes.deleteError'), 'error')
    }
  }

  return (
    <div className={s.wrap}>
      <header className={s.header}>
        <div>
          <h1><i className="fa-solid fa-horse-head" /> {t('adminHorseTypes.title')}</h1>
          <p>{t('adminHorseTypes.subtitle')}</p>
        </div>
      </header>

      <section className={s.tableWrap}>
        <div className={s.tableToolbar}>
          <div className={s.searchBox}>
            <i className="fa-solid fa-magnifying-glass" aria-hidden="true" />
            <input
              type="search"
              placeholder={t('adminHorseTypes.searchPlaceholder')}
              value={breedSearch}
              onChange={event => setBreedSearch(event.target.value)}
            />
          </div>
          <div className={s.toolbarActions}>
            <div className={s.tableStats}>
              {t('adminHorseTypes.count', { shown: filteredBreeds.length, total: horseTypesList.length })}
            </div>
            <button className={s.createBreedBtn} onClick={openCreateModal}>
              <i className="fa-solid fa-plus" /> {t('adminHorseTypes.add')}
            </button>
          </div>
        </div>

        <div className={s.tableScroller}>
          <table className={s.customTable}>
            <thead>
              <tr>
                <th scope="col">ID</th>
                <th scope="col">{t('adminHorseTypes.breed')}</th>
                <th scope="col">{t('adminHorseTypes.description')}</th>
                <th scope="col">{t('adminHorseTypes.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {filteredBreeds.map(breed => (
                <tr key={breed.id}>
                  <td><span className={s.configKey}>#{breed.id}</span></td>
                  <td><div className={s.breedBadge}>{breed.typeName}</div></td>
                  <td><div className={s.breedDesc}>{breed.description || '—'}</div></td>
                  <td>
                    <div className={s.rowActions}>
                      <button className={s.rowActionBtn} onClick={() => openEditModal(breed)}>
                        <i className="fa-solid fa-pen-to-square" /> {t('adminHorseTypes.edit')}
                      </button>
                      <button className={s.rowActionBtnDelete} onClick={() => void deleteBreed(breed)}>
                        <i className="fa-solid fa-trash-can" /> {t('adminHorseTypes.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {filteredBreeds.length === 0 && (
                <tr>
                  <td colSpan={4} className={s.emptyState}>{t('adminHorseTypes.empty')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {modalMode !== null && (
        <div className={s.modalBackdrop} onClick={() => setModalMode(null)}>
          <div className={s.modalCard} onClick={event => event.stopPropagation()}>
            <div className={s.modalHeader}>
              <h2>{t(modalMode === 'create' ? 'adminHorseTypes.createTitle' : 'adminHorseTypes.editTitle')}</h2>
              <button className={s.closeBtn} onClick={() => setModalMode(null)} aria-label={t('adminHorseTypes.cancel')}>
                <i className="fa-solid fa-xmark" />
              </button>
            </div>
            <div className={s.modalBody}>
              <div className={s.modalField}>
                <label htmlFor="horse-breed-name">{t('adminHorseTypes.name')} <span aria-hidden="true">*</span></label>
                <input
                  id="horse-breed-name"
                  type="text"
                  placeholder={t('adminHorseTypes.namePlaceholder')}
                  value={breedName}
                  onChange={event => setBreedName(event.target.value)}
                  autoFocus
                />
              </div>
              <div className={s.modalField}>
                <label htmlFor="horse-breed-description">{t('adminHorseTypes.description')}</label>
                <input
                  id="horse-breed-description"
                  type="text"
                  placeholder={t('adminHorseTypes.descriptionPlaceholder')}
                  value={breedDescription}
                  onChange={event => setBreedDescription(event.target.value)}
                />
              </div>
            </div>
            <div className={s.modalFooter}>
              <button className="btn btn-secondary" onClick={() => setModalMode(null)} disabled={isSaving}>
                {t('adminHorseTypes.cancel')}
              </button>
              <button className="btn btn-primary" onClick={() => void saveBreed()} disabled={isSaving}>
                {isSaving
                  ? t('adminHorseTypes.saving')
                  : t(modalMode === 'create' ? 'adminHorseTypes.create' : 'adminHorseTypes.save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
