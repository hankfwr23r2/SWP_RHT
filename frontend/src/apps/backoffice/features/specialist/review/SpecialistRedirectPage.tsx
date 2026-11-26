import { Navigate, useLocation, useParams } from 'react-router'

export default function SpecialistRedirectPage() {
  const { id } = useParams()
  const { pathname, search, hash } = useLocation()
  const target = pathname.endsWith('/missing-documents') ? 'legal' : 'verification'
  return <Navigate replace to={`/specialist/${target}${id ? `/${id}` : ''}${search}${hash}`} />
}
