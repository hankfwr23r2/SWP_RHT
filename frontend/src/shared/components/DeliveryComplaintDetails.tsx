import { useTranslation } from 'react-i18next'
import type { Complaint } from '@shared/api/deliveryComplaints'
import { FilePreviewButton } from '@shared/ui/FilePreviewButton'
import s from './DeliveryComplaintDetails.module.css'

export function DeliveryComplaintDetails({ complaint, horseName }: { complaint: Complaint; horseName?: string }) {
  const { t } = useTranslation()
  return (
    <div className={s.stack}>
      <p role="status">{t(complaint.status === 'CLOSED' ? 'deliveryComplaint.closed' : 'deliveryComplaint.open')}</p>
      <dl className={s.details}>
        <dt>{t('deliveryComplaint.horse')}</dt>
        <dd>{horseName ?? (complaint.bookingHorseId ?? t('deliveryComplaint.wholeBooking'))}</dd>
        <dt>{t('deliveryComplaint.description')}</dt>
        <dd>{complaint.description}</dd>
        {complaint.reply && <><dt>{t('deliveryComplaint.reply')}</dt><dd>{complaint.reply}</dd></>}
        {complaint.decision && <><dt>{t('deliveryComplaint.decision')}</dt><dd>{t(`deliveryComplaint.${complaint.decision}`)}</dd></>}
        {complaint.decisionReason && <><dt>{t('deliveryComplaint.reason')}</dt><dd>{complaint.decisionReason}</dd></>}
      </dl>
      {!!complaint.photos?.length && (
        <ul className={s.stack}>
          {complaint.photos.map(photo => photo.fileUrl && (
            <li key={photo.id}>
              <span>{photo.fileName ?? t('deliveryComplaint.photos')}</span>
              <FilePreviewButton fileName={photo.fileName ?? t('deliveryComplaint.photos')}
                fileUrl={photo.fileUrl} label={t('ui.previewFile')} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
