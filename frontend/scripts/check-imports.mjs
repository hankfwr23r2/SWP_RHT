// Kiểm chiều import mà oxlint không diễn đạt được (AGENTS.md mục 8.3):
//   R3. shared/ui không dùng shared/api, shared/services, shared/components.
//   R4. Tính năng không import tính năng khác. Tính năng là apps/customer/features/<x>,
//       hoặc apps/backoffice/features/<vai-trò>/<x> (vai trò không có thư mục con thì cả vai trò là một tính năng).
//   R6. axios và fetch chỉ dùng trong shared/api.
// File có từ trước khi có kiểm tra này nằm trong EXCEPTIONS. Danh sách chỉ được rút ngắn:
// file trong danh sách mà đã hết vi phạm thì script báo lỗi để gỡ khỏi danh sách.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, normalize, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const FRONTEND = join(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(FRONTEND, 'src')

const EXCEPTIONS = {
  R3: [
    'src/shared/ui/CaptureField.tsx',
    'src/shared/ui/ChangePasswordModal.tsx',
    'src/shared/ui/InsuranceDocuments.tsx',
  ],
  R4: [
    'src/apps/backoffice/features/coordinator/fleet-plan/FleetPlanPage.tsx',
    'src/apps/backoffice/features/coordinator/fleet/FleetPage.tsx',
    'src/apps/customer/features/auth/LoginPage.tsx',
  ],
  R6: [
    'src/shared/services/termsConsent.test.ts',
    'src/shared/ui/baseMap.ts',
  ],
}

const ALIASES = { '@shared/': 'shared/', '@customer/': 'apps/customer/', '@backoffice/': 'apps/backoffice/' }
const EXTENSIONS = ['', '.ts', '.tsx', '.js', '/index.ts', '/index.tsx']

function sourceFiles(dir) {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.(ts|tsx)$/.test(name) ? [path] : []
  })
}

const toSrc = path => relative(SRC, path).split(sep).join('/')

// Đường dẫn trong src/ của module được import, hoặc null nếu là thư viện ngoài
function resolveImport(file, specifier) {
  let base
  if (specifier.startsWith('.')) base = normalize(join(dirname(file), specifier))
  else {
    const alias = Object.keys(ALIASES).find(prefix => specifier.startsWith(prefix))
    if (!alias) return null
    base = join(SRC, ALIASES[alias] + specifier.slice(alias.length))
  }
  const found = EXTENSIONS.map(ext => base + ext).find(path => existsSync(path) && statSync(path).isFile())
  return toSrc(found ?? base)
}

function featureOf(srcPath) {
  const parts = srcPath.split('/')
  if (parts[0] !== 'apps' || parts[2] !== 'features' || parts.length < 5) return null
  const rest = parts.slice(3)
  if (parts[1] === 'customer') return `customer/${rest[0]}`
  return rest.length >= 3 ? `backoffice/${rest[0]}/${rest[1]}` : `backoffice/${rest[0]}`
}

const IMPORT = /(?:\bfrom\s*|\bimport\s*\(\s*|^\s*import\s+)['"]([^'"]+)['"]/gm
const FETCH_CALL = /(?<![\w.$])fetch\s*\(/

const violations = { R3: new Map(), R4: new Map(), R6: new Map() }
const report = (rule, file, detail) => {
  const list = violations[rule].get(file) ?? []
  violations[rule].set(file, [...list, detail])
}

for (const path of sourceFiles(SRC)) {
  const file = toSrc(path)
  const text = readFileSync(path, 'utf8')
  const feature = featureOf(file)
  const inSharedApi = file.startsWith('shared/api/')

  for (const [, specifier] of text.matchAll(IMPORT)) {
    if (!inSharedApi && (specifier === 'axios' || specifier.startsWith('axios/'))) report('R6', file, `import '${specifier}'`)
    const target = resolveImport(path, specifier)
    if (!target) continue
    if (file.startsWith('shared/ui/') && /^shared\/(api|services|components)\//.test(target)) report('R3', file, `import '${specifier}'`)
    const targetFeature = featureOf(target)
    if (feature && targetFeature && targetFeature !== feature) report('R4', file, `import '${specifier}' (tính năng ${targetFeature})`)
  }
  if (!inSharedApi && FETCH_CALL.test(text)) report('R6', file, 'gọi fetch(...)')
}

const MESSAGES = {
  R3: 'shared/ui không dùng shared/api, shared/services, shared/components. Lấy dữ liệu ở trang hoặc hook rồi truyền props.',
  R4: 'Tính năng không import tính năng khác. Cần dùng chung thì chuyển lên shared/components hoặc shared/hooks.',
  R6: 'axios và fetch chỉ dùng trong shared/api.',
}

const errors = []
for (const rule of Object.keys(violations)) {
  const allowed = new Set(EXCEPTIONS[rule].map(file => file.replace(/^src\//, '')))
  for (const [file, details] of violations[rule]) {
    if (!allowed.has(file)) errors.push(`src/${file}: ${rule} ${MESSAGES[rule]}\n    ${details.join('\n    ')}`)
  }
  for (const file of allowed) {
    if (!violations[rule].has(file)) errors.push(`src/${file}: đã hết vi phạm ${rule}, gỡ khỏi EXCEPTIONS.${rule} trong scripts/check-imports.mjs`)
  }
}

if (errors.length) {
  console.error(errors.join('\n'))
  console.error(`\ncheck-imports: ${errors.length} lỗi (AGENTS.md mục 8.3)`)
  process.exit(1)
}
console.log('check-imports: không có vi phạm mới')
