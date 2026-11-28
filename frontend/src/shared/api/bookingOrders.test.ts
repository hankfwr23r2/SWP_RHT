// @vitest-environment jsdom
import { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, expect, it } from 'vitest'
import { bookingOrdersApi, type BookingCreateRequest } from './bookingOrders'
import apiClient from './client'

const originalAdapter = apiClient.defaults.adapter
const sent: InternalAxiosRequestConfig[] = []

beforeEach(() => {
  sent.length = 0
  apiClient.defaults.adapter = async config => {
    sent.push(config)
    return { data: { status: 'success', message: 'ok', data: { id: 42 } }, status: 200,
      statusText: 'OK', headers: new AxiosHeaders(), config }
  }
})
afterEach(() => { apiClient.defaults.adapter = originalAdapter })

const pdf = (name: string) => new File(['%PDF-1.7'], name, { type: 'application/pdf' })

it('giữ thứ tự các tệp hợp đồng trong yêu cầu multipart tạo đơn', async () => {
  const request: BookingCreateRequest = {
    bookingType: 'LOCAL', requestedPickupDate: '2099-01-01', addresses: [], parties: [],
    horses: [1, 2].map(horseId => ({ horseId, stallTypeCode: 'STANDARD', insurance: { coverageOption: 'PERSONAL' } })),
    acceptedServiceTerms: true,
  }
  const files = [pdf('first.pdf'), pdf('second.pdf')]
  await bookingOrdersApi.create(request, files)

  expect(sent[0].url).toBe('/bookings')
  expect(sent[0].headers.getContentType()).toBe('multipart/form-data')
  const form = sent[0].data as FormData
  expect(form.get('request')).toMatchObject({ type: 'application/json' })
  expect(form.getAll('insuranceFiles')).toEqual(files)
})

it('gửi dữ liệu bổ sung và tệp mới trong cùng yêu cầu', async () => {
  const file = pdf('updated.pdf')
  await bookingOrdersApi.reviseInsurance(42, 8, {
    insurerName: 'Hãng bảo hiểm B', policyNumber: 'P2', validFrom: '2099-01-01', validTo: '2099-01-10',
  }, file)

  expect(sent[0].url).toBe('/bookings/42/insurances/8/revise')
  expect(sent[0].headers.getContentType()).toBe('multipart/form-data')
  const form = sent[0].data as FormData
  expect(form.get('request')).toMatchObject({ type: 'application/json' })
  expect(form.get('file')).toBe(file)
})
