// Trang điều khoản và chính sách: mỗi văn bản là một ảnh hoặc PDF do nhà xe cung cấp (POLICY_DOCS).
import { Link, useSearchParams } from 'react-router'
import { POLICY_DOCS, type PolicyDocId } from '@shared/config/business-rules'

const IDS = Object.keys(POLICY_DOCS) as PolicyDocId[]
const isDoc = (v: string | null): v is PolicyDocId => IDS.some(id => id === v)

export default function PolicyPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('doc')
  const id = isDoc(q) ? q : IDS[0]
  const doc = POLICY_DOCS[id]
  const pdf = doc.file.toLowerCase().endsWith('.pdf')
  return (
    <div className="page">
      <div className="wrap">
        <div className="breadcrumb"><Link to="/">Trang chủ</Link> / <span className="text-orange font-semibold">Điều khoản và chính sách</span></div>
        <div className="page-header"><h1>{doc.title}</h1></div>
        <div role="tablist" aria-label="Văn bản" style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
          {IDS.map(k => (
            <button key={k} role="tab" aria-selected={k === id} className={`btn btn-sm ${k === id ? 'btn-primary' : 'btn-ghost'}`} onClick={() => setParams({ doc: k }, { replace: true })}>{POLICY_DOCS[k].title}</button>
          ))}
        </div>
        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {pdf
            ? <iframe src={doc.file} title={doc.title} style={{ width: '100%', height: '80vh', border: 0 }} />
            : <img src={doc.file} alt={doc.title} style={{ display: 'block', width: '100%', maxWidth: 900, margin: '0 auto' }} />}
        </div>
        <p className="form-hint" style={{ marginTop: 12 }}><a href={doc.file} target="_blank" rel="noopener" className="text-orange font-semibold">Mở tệp trong tab mới</a></p>
      </div>
    </div>
  )
}
