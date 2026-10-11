// Tên ảnh chụp trực tiếp: capture_<mốc thời gian>.jpg. Mốc thời gian do hệ thống gắn, người dùng không sửa được.
export const captureName = () => `capture_${Date.now()}.jpg`
export const captureTime = (name?: string) => { const m = /capture_(\d+)\./.exec(name ?? ''); return m ? Number(m[1]) : undefined }
