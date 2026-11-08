import { describe, expect, it } from 'vitest'
import { auditChanges, auditDefaults, auditQuery } from './systemLogs'

describe('audit dates and pagination', () => {
  it('defaults to seven calendar days in Vietnam even around UTC midnight', () => {
    expect(auditDefaults(new Date('2026-11-07T18:00:00Z'))).toEqual({ from: '2026-11-02', to: '2026-11-08' })
  })
  it('includes the entire end date using an exclusive next midnight', () => {
    expect(auditQuery(new URLSearchParams('from=2026-11-08&to=2026-11-08&target=USER&page=2&size=50'), auditDefaults())).toEqual({
      from: '2026-11-07T17:00:00.000Z', to: '2026-11-08T17:00:00.000Z', target: 'USER', page: 2, size: 50,
    })
  })
  it('rejects impossible dates, reversed ranges and invalid pagination from copied URLs', () => {
    for (const query of ['from=2026-02-30&to=2026-03-01', 'from=2026-11-09&to=2026-11-08', 'page=-1', 'size=10000', 'page=NaN']) {
      expect(() => auditQuery(new URLSearchParams(query), auditDefaults())).toThrow()
    }
  })
})

describe('audit before and after', () => {
  it('shows only changed fields and retains null and false', () => {
    expect(auditChanges('{"name":"Same","phone":"123","required":true}', '{"name":"Same","phone":null,"required":false}', false)).toEqual([
      { field: 'phone', before: '123', after: null }, { field: 'required', before: true, after: false },
    ])
  })
  it('supports creation and deletion snapshots', () => {
    expect(auditChanges(null, '{"name":"New"}', false)).toEqual([{ field: 'name', before: undefined, after: 'New' }])
    expect(auditChanges('{"name":"Old"}', null, false)).toEqual([{ field: 'name', before: 'Old', after: undefined }])
  })
  it('does not interpret historical descriptions as structured change data', () => {
    expect(auditChanges('ACTIVE', 'Khóa tài khoản', true)).toEqual([])
    expect(auditChanges(null, 'malformed', false)).toBeNull()
    expect(auditChanges(null, null, false)).toEqual([])
  })
})
