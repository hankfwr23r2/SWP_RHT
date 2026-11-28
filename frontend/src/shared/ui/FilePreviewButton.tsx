import { useState } from 'react'
import { rememberPreview } from '@shared/lib/previews'
import { Button } from './Button'
import { ImageLightbox } from './ImageThumb'
import s from './FilePreviewButton.module.css'

interface FilePreviewButtonProps {
  fileName: string
  label: string
  file?: File
  fileUrl?: string
}

export function FilePreviewButton({ fileName, label, file, fileUrl }: FilePreviewButtonProps) {
  const [open, setOpen] = useState(false)
  const source = file ? file.name : fileUrl ? new URL(fileUrl, window.location.href).toString() : undefined
  const fileType = file?.type || (/\.pdf$/i.test(fileName) ? 'application/pdf' : undefined)

  if (!source) return null

  const openPreview = () => {
    if (file) rememberPreview(file.name, file)
    setOpen(true)
  }

  return (
    <div className={s.action}>
      <Button size="sm" onClick={openPreview} aria-label={`${label} ${fileName}`}>{label}</Button>
      {open && <ImageLightbox name={source} title={fileName} fileType={fileType} onClose={() => setOpen(false)} />}
    </div>
  )
}
