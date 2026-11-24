// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import '@shared/i18n'
import apiClient from '@shared/api/client'
import { catalogApi } from '@shared/api/catalog'
import { parseMapPoint } from '@shared/components/MapPicker'
import { ToastProvider } from '@shared/ui/toast'
import AddressBookPage from './AddressBookPage'
import { addressesLoader } from './loaders'

vi.mock('@shared/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))
vi.mock('@shared/ui/baseMap', () => ({ BASE_MAP_OPTIONS: {}, addBaseLayer: vi.fn() }))
const { setView } = vi.hoisted(() => ({ setView: vi.fn() }))
vi.mock('leaflet', () => ({
  default: {
    map: () => ({
      setView, on: vi.fn(), off: vi.fn(), remove: vi.fn(),
      getBounds: () => ({ contains: () => true }), getZoom: () => 10,
    }),
    circleMarker: () => ({ addTo() { return this }, setLatLng: vi.fn(), remove: vi.fn() }),
    control: { zoom: () => ({ addTo() { return this }, remove: vi.fn() }) },
  },
}))

const listPath = '/customers/me/addresses'
const response = (data: unknown, status = 200) => ({ status, data: { data } })
const address = {
  id: 7, label: 'Chuồng nhà', addressLine: '12 Đường Đua', countryCode: 'VN',
  provinceCode: '79', ward: 'Tân Phú', latitude: 10.123457, longitude: 106.123457, defaultAddress: true,
}

function renderPage() {
  const router = createMemoryRouter([{
    path: '/',
    loader: addressesLoader,
    element: <ToastProvider><AddressBookPage /></ToastProvider>,
    HydrateFallback: null,
  }])
  render(<RouterProvider router={router} />)
  return router
}

function select(name: RegExp, option: string) {
  fireEvent.click(screen.getByRole('combobox', { name }))
  fireEvent.click(screen.getByRole('option', { name: option }))
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('MapPicker coordinates', () => {
  it('rounds to six decimal places and accepts zero and boundary values', () => {
    expect(parseMapPoint('10.12345678', '106.12345678')).toEqual({ latitude: 10.123457, longitude: 106.123457 })
    expect(parseMapPoint('0', '-180')).toEqual({ latitude: 0, longitude: -180 })
  })

  it('rejects incomplete, nonfinite or out-of-range pairs', () => {
    expect(parseMapPoint(' ', '106')).toBeNull()
    expect(parseMapPoint('Infinity', '106')).toBeNull()
    expect(parseMapPoint('90.0000001', '106')).toBeNull()
    expect(parseMapPoint('10', '-181')).toBeNull()
  })
})

describe('catalog cache', () => {
  it('shares in-flight requests per country and retries a failed request', async () => {
    const get = vi.mocked(apiClient.get)
    const provinces = [{ code: 'K1', name: 'Kampot', countryCode: 'KH' }]
    get.mockRejectedValueOnce(new Error('Network')).mockResolvedValueOnce(response(provinces))
    const first = catalogApi.provinces(' kh ')
    expect(catalogApi.provinces('KH')).toBe(first)
    await expect(first).rejects.toThrow('Network')
    await expect(catalogApi.provinces('KH')).resolves.toEqual(provinces)
    await expect(catalogApi.provinces('kh')).resolves.toEqual(provinces)
    expect(get).toHaveBeenCalledTimes(2)
    expect(get).toHaveBeenLastCalledWith('/provinces', { params: { country: 'KH' } })
  })
})

describe('Address book', () => {
  it('retries a load error and confirms removal before deleting', async () => {
    let reject!: (error: Error) => void
    let addresses = [{ id: address.id, label: address.label, defaultAddress: true }]
    vi.mocked(apiClient.get).mockImplementation((path) => {
      if (path === listPath) return Promise.resolve(response(addresses))
      return Promise.resolve(response([]))
    }).mockImplementationOnce(() => new Promise((_, fail) => { reject = fail }))
    vi.mocked(apiClient.delete).mockImplementation(async () => {
      addresses = []
      return response(null)
    })
    const router = renderPage()
    expect(await screen.findByRole('status')).toBeTruthy()
    await act(async () => reject(new Error('Failed request')))
    fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Mặc định')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Xóa' }))
    const dialog = screen.getByRole('alertdialog', { name: 'Xóa địa chỉ Chuồng nhà?' })
    expect(apiClient.delete).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xóa' }))
    expect(await screen.findByText('Bạn chưa lưu địa chỉ nào')).toBeTruthy()
    expect(apiClient.delete).toHaveBeenCalledWith(`${listPath}/7`)
    router.dispose()
  })

  it('requires a location, rounds keyboard input and places server errors under their fields', async () => {
    let addresses: typeof address[] = []
    vi.mocked(apiClient.get).mockImplementation(async (path, config) => {
      if (path === listPath) return response(addresses)
      if (path === '/countries') return response([{ code: 'VN', name: 'Việt Nam' }])
      if (path === '/provinces' && (config?.params as { country?: string } | undefined)?.country === 'VN') {
        return response([{ code: '79', name: 'TP. Hồ Chí Minh', countryCode: 'VN', latitude: 10.776, longitude: 106.7 }])
      }
      throw new Error('Unexpected endpoint')
    })
    const post = vi.mocked(apiClient.post)
    post.mockResolvedValueOnce(response({ addressLine: 'Địa chỉ chưa đầy đủ.' }, 400))
      .mockResolvedValueOnce({ status: 400, data: { message: 'Vị trí ghim cách tỉnh đã chọn quá 200 km.', data: null } })
      .mockImplementationOnce(async () => {
        addresses = [address]
        return response(address, 201)
      })
    const router = renderPage()
    expect(await screen.findByText('Bạn chưa lưu địa chỉ nào')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Thêm địa chỉ' }))
    const country = screen.getByRole('combobox', { name: /Quốc gia/ })
    await waitFor(() => expect((country as HTMLButtonElement).disabled).toBe(false))
    select(/Quốc gia/, 'Việt Nam')
    const province = screen.getByRole('combobox', { name: /Tỉnh hoặc thành phố/ })
    await waitFor(() => expect((province as HTMLButtonElement).disabled).toBe(false))
    select(/Tỉnh hoặc thành phố/, 'TP. Hồ Chí Minh')

    fireEvent.change(screen.getByRole('textbox', { name: /Tên gợi nhớ/ }), { target: { value: address.label } })
    const street = screen.getByRole('textbox', { name: /Địa chỉ chi tiết/ })
    fireEvent.change(street, { target: { value: '12' } })
    fireEvent.change(screen.getByRole('textbox', { name: /Phường hoặc xã/ }), { target: { value: address.ward } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'Đặt làm địa chỉ mặc định' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(post).not.toHaveBeenCalled()
    // Quên ghim: bản đồ tự mở kèm lỗi
    expect(await screen.findByText(/Hãy chọn một vị trí/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Ẩn bản đồ' })).toBeTruthy()
    await waitFor(() => expect(setView).toHaveBeenLastCalledWith([10.776, 106.7], 10, { animate: false }))
    fireEvent.change(await screen.findByRole('textbox', { name: 'Vĩ độ' }), { target: { value: '10.12345678' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Kinh độ' }), { target: { value: '106.12345678' } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    const error = await screen.findByText('Địa chỉ chưa đầy đủ.')
    expect(street.getAttribute('aria-describedby')).toBe(error.id)
    fireEvent.change(street, { target: { value: address.addressLine } })
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByText('Vị trí ghim quá xa TP. Hồ Chí Minh. Hãy ghim lại hoặc chọn tỉnh khác.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByRole('heading', { name: address.label })).toBeTruthy()
    const { id: _id, ...body } = address
    expect(post).toHaveBeenLastCalledWith(listPath, body, expect.any(Object))
    router.dispose()
  })

  it('keeps existing coordinates when editing the address and its default flag', async () => {
    let current = address
    vi.mocked(apiClient.get).mockImplementation(async path => {
      if (path === listPath) return response([current])
      if (path === '/countries') return response([{ code: 'VN', name: 'Việt Nam' }])
      return response([{ code: '79', name: 'TP. Hồ Chí Minh', countryCode: 'VN' }])
    })
    vi.mocked(apiClient.put).mockImplementation(async (_path, body) => {
      current = { ...current, ...body as typeof address }
      return response(current)
    })
    const router = renderPage()
    const edit = await screen.findByRole('button', { name: 'Sửa' })
    fireEvent.click(edit)
    const label = screen.getByRole('textbox', { name: /Tên gợi nhớ/ })
    expect((label as HTMLInputElement).value).toBe(address.label)
    fireEvent.change(label, { target: { value: 'Chuồng mới' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'Đặt làm địa chỉ mặc định' }))
    fireEvent.click(screen.getByRole('button', { name: 'Lưu' }))
    expect(await screen.findByRole('heading', { name: 'Chuồng mới' })).toBeTruthy()
    expect(screen.queryByText('Mặc định')).toBeNull()
    const { id, ...body } = address
    expect(apiClient.put).toHaveBeenCalledWith(`${listPath}/${id}`, {
      ...body, label: 'Chuồng mới', defaultAddress: false,
    }, expect.any(Object))
    router.dispose()
  })
})
