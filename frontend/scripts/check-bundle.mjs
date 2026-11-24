// Kiểm dung lượng tải lần đầu của hai ứng dụng sau khi build: cộng JS và CSS mà trang HTML nạp ngay, tính theo gzip.
// Vượt ngưỡng thì báo lỗi. Ngưỡng chỉ được hạ xuống, muốn nâng phải có lý do và người dùng đồng ý.
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

const DIST = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
const BUDGET_KB = { 'index.html': 214, 'backoffice.html': 225 }

let failed = false
for (const [page, limit] of Object.entries(BUDGET_KB)) {
  const files = [...readFileSync(join(DIST, page), 'utf8').matchAll(/(?:src|href)="(\/assets\/[^"]+\.(?:js|css))"/g)].map(m => m[1])
  const kb = files.reduce((sum, file) => sum + gzipSync(readFileSync(join(DIST, file))).length, 0) / 1024
  const ok = kb <= limit
  failed ||= !ok
  console.log(`${ok ? 'OK ' : 'VƯỢT'} ${page}: ${kb.toFixed(1)} KB gzip (ngưỡng ${limit} KB, ${files.length} file)`)
}
if (failed) {
  console.error('Gói tải đầu vượt ngưỡng. Tách phần không cần ngay bằng import() hoặc lazyPage.')
  process.exit(1)
}
