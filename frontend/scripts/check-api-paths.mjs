// Kiểm đường dẫn API trong shared/api/*.ts theo đặc tả OpenAPI do backend sinh (openapi/openapi.json).
// Mỗi chuỗi bắt đầu bằng "/" (nháy đơn, nháy kép hoặc template) được coi là đường dẫn API. Phần ${...} khớp với một tham số
// {x} của đặc tả, phần sau "?" bị bỏ. Chuỗi hợp lệ khi khớp trọn một đường dẫn trong đặc tả, hoặc là phần đầu hay phần cuối
// (theo từng đoạn giữa hai dấu "/") của một đường dẫn, vì code hay ghép gốc và đuôi: `${base}/review-detail`.
// Không kiểm phương thức HTTP. Chuỗi bắt đầu bằng "/" mà không phải API thì ghi vào NOT_API.
import { readdirSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..')
const API_DIR = join(FRONTEND, 'src', 'shared', 'api')

// Chuỗi trong shared/api bắt đầu bằng "/" nhưng không phải đường dẫn API
const NOT_API = new Set([
  '/backoffice', // tiền tố URL của ứng dụng nhân viên
  '/api/v1', // context path
  '/auth/login', // client.ts so khớp chuỗi để không refresh lặp, không gọi endpoint này
])

// Hàm cũ gọi endpoint backend không còn (27/11/2026). Chỉ được rút ngắn: gỡ hàm cũ thì gỡ dòng ở đây,
// dòng không còn vi phạm thì script báo lỗi để gỡ.
const EXCEPTIONS = new Set([
  'horses.ts  /horse-types/${id}',
  'horses.ts  /horse-types/${id}/insured-value',
  'horses.ts  /horses/${id}/location',
  'horses.ts  /horses/${id}/journey-logs',
  'payments.ts  /payments/vnpay/create',
  'payments.ts  /payments/cancel',
  'payments.ts  /payments/booking/${bookingId}/summary',
  'payments.ts  /payments/booking/${bookingId}',
])

const spec = JSON.parse(readFileSync(join(FRONTEND, 'openapi', 'openapi.json'), 'utf8'))
const specPaths = Object.keys(spec.paths).map(path => path.split('/').filter(Boolean))

const PARAM = '\u0000'
const isParam = segment => /^\{[^}]+\}$/.test(segment)

// `/bookings/${id}/documents?x=1` -> ['bookings', PARAM, 'documents']
function segmentsOf(literal) {
  const path = literal.replace(/\$\{[^}]*\}/g, PARAM).split('?')[0]
  return path.split('/').filter(Boolean)
}

function segmentMatches(code, specSegment) {
  if (code.includes(PARAM)) return isParam(specSegment) || new RegExp(`^${code.split(PARAM).map(escape).join('.+')}$`).test(specSegment)
  return code === specSegment
}

const escape = text => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

function sameAt(code, path, offset) {
  return code.every((segment, index) => segmentMatches(segment, path[offset + index]))
}

function known(code) {
  if (code.length === 0) return true
  return specPaths.some(path => {
    if (code.length > path.length) return false
    // trọn đường dẫn, phần đầu, hoặc phần cuối
    return sameAt(code, path, 0) || sameAt(code, path, path.length - code.length)
  })
}

const LITERAL = /(['"`])(\/(?:(?!\1)[^\\\n]|\\.)*?)\1/g

const problems = []
const excepted = new Set()
let checked = 0
for (const name of readdirSync(API_DIR)) {
  if (!/\.ts$/.test(name) || /\.test\.ts$/.test(name)) continue
  const lines = readFileSync(join(API_DIR, name), 'utf8').split('\n')
  lines.forEach((line, index) => {
    if (/^\s*(\/\/|\*)/.test(line)) return
    for (const match of line.matchAll(LITERAL)) {
      const literal = match[2]
      if (!/^\/[A-Za-z$]/.test(literal) || literal.startsWith('/${') || NOT_API.has(literal)) continue
      checked++
      if (known(segmentsOf(literal))) continue
      const key = `${name}  ${literal}`
      if (EXCEPTIONS.has(key)) excepted.add(key)
      else problems.push(`src/shared/api/${name}:${index + 1}  ${literal}`)
    }
  })
}

for (const key of EXCEPTIONS) {
  if (!excepted.has(key)) problems.push(`EXCEPTIONS còn dòng "${key}" nhưng đã hết vi phạm: gỡ khỏi danh sách`)
}

if (problems.length) {
  console.error('check-api-paths: đường dẫn không có trong openapi/openapi.json (sinh lại đặc tả hoặc sửa đường dẫn):')
  for (const problem of problems) console.error('  ' + problem)
  process.exit(1)
}
console.log(`check-api-paths: ${checked - excepted.size} đường dẫn khớp đặc tả, ${excepted.size} ngoại lệ cũ`)
