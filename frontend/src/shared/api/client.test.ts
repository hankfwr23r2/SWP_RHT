import axios, { AxiosError, AxiosHeaders } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import apiClient, { tokenStorage } from './client'

function makeToken(expirationSeconds: number): string {
  const payload = btoa(JSON.stringify({ exp: expirationSeconds }))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
  return `header.${payload}.signature`
}

it('explains an HTML 413 response from the reverse proxy', async () => {
  await expect(apiClient.get('/upload-test', { adapter: async config => {
    const response = { data: '<h1>413 Request Entity Too Large</h1>', status: 413, statusText: 'Too Large', headers: new AxiosHeaders(), config }
    throw new AxiosError('Request failed', 'ERR_BAD_REQUEST', config, {}, response)
  } })).rejects.toMatchObject({ status: 413, message: expect.stringContaining('5 MB') })
})

describe('tokenStorage proactive refresh', () => {
  afterEach(() => {
    tokenStorage.remove()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('refreshes the access token one minute before expiration while idle', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-26T10:00:00.000Z'))

    const values = new Map<string, string>()
    vi.stubGlobal('window', { location: { pathname: '/' } })
    vi.stubGlobal('localStorage', {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => values.set(key, value),
      removeItem: (key: string) => values.delete(key),
    })

    const renewedToken = makeToken(Date.now() / 1000 + 300)
    const refreshResponse = {
      data: { data: { accessToken: renewedToken } },
      status: 200,
      statusText: 'OK',
      headers: new AxiosHeaders(),
      config: { headers: new AxiosHeaders() },
    }
    const refreshRequest = vi.spyOn(axios, 'post').mockResolvedValue(refreshResponse)

    tokenStorage.set(makeToken(Date.now() / 1000 + 120))

    await vi.advanceTimersByTimeAsync(59_999)
    expect(refreshRequest).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)

    expect(refreshRequest).toHaveBeenCalledOnce()
    expect(values.get('SWP_ACCESS_TOKEN_customer')).toBe(renewedToken)
  })
})
