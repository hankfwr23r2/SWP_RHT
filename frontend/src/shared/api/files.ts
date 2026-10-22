import apiClient, { type ApiResponse } from './client'

export interface FileUploadResponse {
  url: string
  originalFilename: string
  contentType: string
  size: number
}

export const fileApi = {
  /**
   * Tải một tệp lên hệ thống lưu trữ.
   * @param file File ảnh hoặc PDF
   * @param folder Thư mục phân loại (ví dụ: 'horses', 'clearance', 'settlement')
   */
  upload: async (file: File, folder = 'documents'): Promise<FileUploadResponse> => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folder', folder)

    const res = await apiClient.post<ApiResponse<FileUploadResponse>>('/files/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return res.data.data
  },

  /**
   * Tải nhiều tệp lên hệ thống lưu trữ.
   * @param files Danh sách File
   * @param folder Thư mục phân loại
   */
  uploadMultiple: async (files: File[], folder = 'documents'): Promise<FileUploadResponse[]> => {
    const formData = new FormData()
    files.forEach(f => formData.append('files', f))
    formData.append('folder', folder)

    const res = await apiClient.post<ApiResponse<FileUploadResponse[]>>('/files/upload-multiple', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return res.data.data
  },
}
