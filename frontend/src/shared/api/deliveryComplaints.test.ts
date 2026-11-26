// @vitest-environment jsdom
import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, expect, it } from 'vitest'
import apiClient from './client'
import { deliveryComplaintsApi, DeliveryComplaintValidationError } from './deliveryComplaints'

const originalAdapter = apiClient.defaults.adapter

afterEach(() => { apiClient.defaults.adapter = originalAdapter })

it.each(['customer', 'manager'] as const)('preserves photos in the %s complaint upload after Axios transforms the request', async role => {
  let sent: InternalAxiosRequestConfig | undefined
  apiClient.defaults.adapter = async config => {
    sent = config
    return { data: { status: 'success', message: 'ok', data: { bookingId: 42 } }, status: 200,
      statusText: 'OK', headers: new AxiosHeaders(), config }
  }
  const photo = new File([new Uint8Array([1, 2, 3])], 'evidence.png', { type: 'image/png' })
  const body = new FormData()
  body.append('description', 'Delivery complaint')
  body.append('bookingHorseId', '7')
  body.append('photos', photo)
  body.append('photos', photo)

  const submit = role === 'customer' ? deliveryComplaintsApi.submit : deliveryComplaintsApi.submitOnBehalf
  await expect(submit(42, body)).resolves.toEqual({ bookingId: 42 })

  expect(sent?.url).toBe(`/bookings/42/delivery-complaints${role === 'manager' ? '/submit-on-behalf' : ''}`)
  expect(sent?.headers.getContentType()).toBe('multipart/form-data')
  expect(sent?.data).toBe(body)
  const form = body
  expect(form.getAll('photos')).toEqual([photo, photo])
  expect(form.get('description')).toBe('Delivery complaint')
  expect(form.get('bookingHorseId')).toBe('7')
})

it('keeps confirmation, reply and conclusion requests as JSON', async () => {
  const sent: InternalAxiosRequestConfig[] = []
  apiClient.defaults.adapter = async config => {
    sent.push(config)
    return { data: { status: 'success', message: 'ok', data: { bookingId: 42 } }, status: 200,
      statusText: 'OK', headers: new AxiosHeaders(), config }
  }

  await deliveryComplaintsApi.confirm(42)
  await deliveryComplaintsApi.reply(42, { reply: 'Reviewed' })
  await deliveryComplaintsApi.resolve(42, { decision: 'ACCEPTED', reason: 'Evidence reviewed' })

  expect(sent.map(config => config.headers.getContentType())).toEqual(Array(3).fill('application/json'))
  expect(sent.map(config => JSON.parse(config.data as string))).toEqual([
    { confirmed: true }, { reply: 'Reviewed' }, { decision: 'ACCEPTED', reason: 'Evidence reviewed' },
  ])
})

it('preserves backend photo validation errors for the form', async () => {
  apiClient.defaults.adapter = async config => ({ status: 400, statusText: 'Bad Request',
    headers: new AxiosHeaders(), config,
    data: { status: 'error', message: 'Photo required', data: { photos: 'Attach at least one photo' } },
  })

  const request = deliveryComplaintsApi.submitOnBehalf(42, new FormData())
  await expect(request).rejects.toBeInstanceOf(DeliveryComplaintValidationError)
  await expect(request).rejects.toMatchObject({ fields: { photos: 'Attach at least one photo' } })
})
