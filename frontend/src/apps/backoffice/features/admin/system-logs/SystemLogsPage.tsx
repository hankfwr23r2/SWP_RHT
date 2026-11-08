import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { systemLogsApi, type SystemLogItem, type SystemLogPage } from '@shared/api/systemLogs'
import { auditChanges, auditDefaults, auditQuery } from '@shared/lib/systemLogs'
import { FormSelect } from '@shared/ui/FormSelect'
import { ROLE_LABEL, type Role } from '@shared/types/role'
import s from './SystemLogs.module.css'

function Filters({ params, defaults, options, apply }: {
  params: URLSearchParams; defaults: { from: string; to: string }; options: Record<string, string[]>; apply: (p: URLSearchParams) => void
}) {
  const { t } = useTranslation()
  const [target, setTarget] = useState(params.get('target') ?? '')
  const [action, setAction] = useState(params.get('action') ?? '')
  const [role, setRole] = useState(params.get('role') ?? '')
  const actions = target ? options[target] ?? [] : [...new Set(Object.values(options).flat())]
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const values = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    for (const key of ['from', 'to', 'actor', 'key']) {
      const value = String(values.get(key) ?? '').trim()
      if (value) next.set(key, value)
    }
    if (target) next.set('target', target)
    if (action) next.set('action', action)
    if (role) next.set('role', role)
    next.set('size', params.get('size') ?? '20')
    apply(next)
  }
  return <form className={s.filters} onSubmit={submit}>
    <label>{t('systemLogs.from')}<input className="form-control" name="from" type="date" required defaultValue={params.get('from') ?? defaults.from} /></label>
    <label>{t('systemLogs.to')}<input className="form-control" name="to" type="date" required defaultValue={params.get('to') ?? defaults.to} /></label>
    <label>{t('systemLogs.group')}<FormSelect value={target} onChange={e => { setTarget(e.target.value); setAction('') }} aria-label={t('systemLogs.group')}>
      <option value="">{t('systemLogs.all')}</option>{Object.keys(options).sort().map(key => <option key={key} value={key}>{t(`systemLogs.targets.${key}`, key)}</option>)}
    </FormSelect></label>
    <label>{t('systemLogs.action')}<FormSelect value={action} onChange={e => setAction(e.target.value)} aria-label={t('systemLogs.action')}>
      <option value="">{t('systemLogs.all')}</option>{actions.map(key => <option key={key} value={key}>{t(`systemLogs.actions.${key}`, key)}</option>)}
    </FormSelect></label>
    <label>{t('systemLogs.actor')}<input className="form-control" name="actor" maxLength={255} defaultValue={params.get('actor') ?? ''} placeholder={t('systemLogs.actorHint')} /></label>
    <label>{t('systemLogs.role')}<FormSelect value={role} onChange={e => setRole(e.target.value)} aria-label={t('systemLogs.role')}>
      <option value="">{t('systemLogs.all')}</option>{(Object.keys(ROLE_LABEL) as Role[]).map(key => <option key={key} value={key.toUpperCase()}>{t(`roles.${key}`, ROLE_LABEL[key])}</option>)}
    </FormSelect></label>
    <label>{t('systemLogs.target')}<input className="form-control" name="key" maxLength={255} defaultValue={params.get('key') ?? ''} placeholder={t('systemLogs.targetHint')} /></label>
    <div className={s.buttons}><button className="btn btn-primary" type="submit">{t('systemLogs.filter')}</button><button className="btn btn-ghost" type="button" onClick={() => apply(new URLSearchParams(defaults))}>{t('systemLogs.reset')}</button></div>
  </form>
}

function Detail({ id, close }: { id: string; close: () => void }) {
  const { t, i18n } = useTranslation()
  const dialog = useRef<HTMLDialogElement>(null)
  const [item, setItem] = useState<SystemLogItem | null>(null)
  const [error, setError] = useState(false)
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    setError(false); setItem(null)
    systemLogsApi.detail(id, controller.signal).then(row => { if (!controller.signal.aborted) setItem(row) }).catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => controller.abort()
  }, [id, retry])
  const changes = item ? auditChanges(item.oldData, item.newData, item.legacy) : []
  const renderValue = (value: unknown): string => {
    if (value === undefined || value === null) return '—'
    if (typeof value === 'boolean') return t(value ? 'systemLogs.yes' : 'systemLogs.no')
    if (Array.isArray(value)) return value.map((entry, index) => `${index + 1}. ${renderValue(entry)}`).join('\n\n') || '—'
    if (typeof value === 'object') return Object.entries(value).map(([key, entry]) => `${t(`systemLogs.fields.${key}`, key)}: ${renderValue(entry)}`).join('\n')
    return typeof value === 'string' ? t(`systemLogs.values.${value}`, value) : String(value)
  }
  return <dialog className={s.dialog} ref={dialog} onCancel={event => { event.preventDefault(); close() }} aria-labelledby="audit-detail-title">
    <div className={s.detailHead}><h2 id="audit-detail-title">{t('systemLogs.detail')} #{id}</h2><button className="btn btn-ghost" onClick={close} autoFocus>{t('common.close')}</button></div>
    {error ? <div role="alert"><p>{t('systemLogs.detailError')}</p><button className="btn btn-outline" onClick={() => setRetry(n => n + 1)}>{t('systemLogs.retry')}</button></div>
      : !item ? <p role="status">{t('common.loading')}</p> : <>
        <dl className={s.metadata}>
          <div><dt>{t('systemLogs.time')}</dt><dd>{timestamp(item.createdAt, i18n.language)}</dd></div>
          <div><dt>{t('systemLogs.actor')}</dt><dd>{item.actorName || t('systemLogs.unknown')} {item.actorRole && `· ${t(`roles.${item.actorRole.toLowerCase()}`, item.actorRole)}`}<small>{item.actorEmail}</small></dd></div>
          <div><dt>{t('systemLogs.action')}</dt><dd>{t(`systemLogs.actions.${item.actionType}`, item.actionType)}</dd></div>
          <div><dt>{t('systemLogs.target')}</dt><dd>{t(`systemLogs.targets.${item.targetTable}`, item.targetTable)} · {item.targetKey}<small>{item.targetLabel}</small></dd></div>
        </dl>
        <p>{item.description || (item.legacy ? t('systemLogs.legacy') : '')}</p>
        {item.legacy ? <div className={s.legacy}><p>{t('systemLogs.legacyHint')}</p><h3>{t('systemLogs.before')}</h3><pre>{item.oldData || '—'}</pre><h3>{t('systemLogs.after')}</h3><pre>{item.newData || '—'}</pre></div>
          : changes === null ? <p role="alert">{t('systemLogs.invalidSnapshot')}</p>
            : !changes.length ? <p>{t('systemLogs.noChanges')}</p> : <div className={s.tableWrap}><table className={s.table}><thead><tr><th>{t('systemLogs.field')}</th><th>{t('systemLogs.before')}</th><th>{t('systemLogs.after')}</th></tr></thead><tbody>{changes.map(change => <tr key={change.field}><th>{t(`systemLogs.fields.${change.field}`, change.field)}</th><td><pre>{renderValue(change.before)}</pre></td><td><pre>{renderValue(change.after)}</pre></td></tr>)}</tbody></table></div>}
      </>}
  </dialog>
}

function timestamp(value: string | null, locale: string) {
  return value ? new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-GB', { timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) + ' (UTC+7)' : '—'
}

export default function SystemLogsPage() {
  const { t, i18n } = useTranslation()
  const [params, setParams] = useSearchParams()
  const [defaults] = useState(auditDefaults)
  useEffect(() => {
    if (!params.has('from') || !params.has('to')) {
      const next = new URLSearchParams(params)
      if (!next.has('from')) next.set('from', defaults.from)
      if (!next.has('to')) next.set('to', defaults.to)
      setParams(next, { replace: true })
    }
  }, [params, setParams, defaults])
  const [options, setOptions] = useState<Record<string, string[]>>({})
  const [optionsError, setOptionsError] = useState(false)
  const [data, setData] = useState<SystemLogPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const filters = new URLSearchParams(params); filters.delete('log')
  const queryKey = filters.toString()
  const detailId = params.get('log')
  useEffect(() => {
    const controller = new AbortController()
    setOptionsError(false)
    systemLogsApi.options(controller.signal).then(result => { if (!controller.signal.aborted) setOptions(result) }).catch(() => { if (!controller.signal.aborted) setOptionsError(true) })
    return () => controller.abort()
  }, [retry])
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setData(null)
    let query: Record<string, string | number>
    try { query = auditQuery(new URLSearchParams(queryKey), defaults) }
    catch (failure) { setError(failure instanceof Error ? failure.message : 'loadError'); setLoading(false); return }
    systemLogsApi.list(query, controller.signal).then(result => { if (!controller.signal.aborted) setData(result) })
      .catch(() => { if (!controller.signal.aborted) setError('loadError') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [queryKey, defaults, retry])
  const navigatePage = (page: number) => { const next = new URLSearchParams(params); next.set('page', String(page)); setParams(next) }
  const close = () => { const next = new URLSearchParams(params); next.delete('log'); setParams(next, { replace: true }) }
  return <div className="page"><div className={`wrap ${s.wrap}`}>
    <div className={s.heading}><div><h1>{t('systemLogs.title')}</h1><p>{t('systemLogs.subtitle')}</p></div><button className="btn btn-outline" onClick={() => setRetry(n => n + 1)} disabled={loading}>{t('systemLogs.refresh')}</button></div>
    <section className={s.panel}>
      <Filters key={queryKey + Object.keys(options).join()} params={params} defaults={defaults} options={options} apply={setParams} />
      {optionsError && <p className={s.message} role="alert">{t('systemLogs.optionsError')} <button className="btn btn-ghost" onClick={() => setRetry(n => n + 1)}>{t('systemLogs.retry')}</button></p>}
      <div className={s.tableWrap} aria-busy={loading}>
        <table className={s.table}><caption className={s.caption}>{t('systemLogs.title')}</caption><thead><tr>{['time', 'actor', 'action', 'target', 'description', 'detail'].map(key => <th key={key}>{t(`systemLogs.${key}`)}</th>)}</tr></thead>
          <tbody>{data?.content.map(item => <tr key={item.id}>
            <td className={s.time}>{timestamp(item.createdAt, i18n.language)}</td>
            <td>{item.actorName || t('systemLogs.unknown')}<small>{item.actorEmail}</small>{item.actorRole && <small>{t(`roles.${item.actorRole.toLowerCase()}`, item.actorRole)}</small>}</td>
            <td>{t(`systemLogs.actions.${item.actionType}`, item.actionType)}</td>
            <td>{t(`systemLogs.targets.${item.targetTable}`, item.targetTable)} · {item.targetKey}<small>{item.targetLabel}</small></td>
            <td>{item.description || (item.legacy ? t('systemLogs.legacy') : '—')}</td>
            <td><button className="btn btn-ghost btn-sm" aria-label={`${t('systemLogs.detail')} #${item.id}`} onClick={() => { const next = new URLSearchParams(params); next.set('log', String(item.id)); setParams(next) }}>{t('systemLogs.view')}</button></td>
          </tr>)}</tbody>
        </table>
      </div>
      {loading ? <p className={s.message} role="status">{t('common.loading')}</p> : error ? <div className={s.message} role="alert"><p>{t(`systemLogs.${error}`)}</p>{error === 'loadError' && <button className="btn btn-outline" onClick={() => setRetry(n => n + 1)}>{t('systemLogs.retry')}</button>}</div>
        : data?.content.length === 0 && <p className={s.message}>{t(data.totalElements ? 'systemLogs.outOfRange' : 'systemLogs.empty')}{data.totalElements > 0 && <button className="btn btn-ghost" onClick={() => navigatePage(0)}>{t('systemLogs.firstPage')}</button>}</p>}
      {data && <div className={s.pagination}>
        <span>{t('systemLogs.count', { count: data.totalElements })} · {t('systemLogs.page', { page: data.number + 1, total: Math.max(1, data.totalPages) })}</span>
        <div className={s.buttons}><label>{t('systemLogs.pageSize')}<select className="form-control" value={data.size} onChange={event => { const next = new URLSearchParams(params); next.set('size', event.target.value); next.delete('page'); setParams(next) }}>{[20, 50, 100].map(size => <option key={size} value={size}>{size}</option>)}</select></label>
          <button className="btn btn-outline btn-sm" disabled={data.number === 0} onClick={() => navigatePage(data.number - 1)}>{t('systemLogs.previous')}</button><button className="btn btn-outline btn-sm" disabled={data.number + 1 >= data.totalPages} onClick={() => navigatePage(data.number + 1)}>{t('systemLogs.next')}</button></div>
      </div>}
    </section>
    {detailId && <Detail key={detailId} id={detailId} close={close} />}
  </div></div>
}
