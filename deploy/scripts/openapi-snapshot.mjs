import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'

const mode = process.argv[2] || 'check'
const snapshotPath = resolve(process.argv[3] || 'docs/api/openapi.json')
const sourceUrl = process.env.CC4C_OPENAPI_URL || 'http://127.0.0.1:4080/v3/api-docs'

if (!['check', 'update'].includes(mode)) {
  throw new Error('Usage: node deploy/scripts/openapi-snapshot.mjs check|update [snapshot]')
}

const parsedUrl = new URL(sourceUrl)
if (!['localhost', '127.0.0.1', '::1'].includes(parsedUrl.hostname)) {
  throw new Error('OpenAPI snapshot source must be a loopback address')
}

const response = await fetch(parsedUrl, { signal: AbortSignal.timeout(15_000) })
if (!response.ok) {
  throw new Error(`OpenAPI endpoint returned HTTP ${response.status}`)
}

const document = await response.json()
validateDocument(document)
const normalized = `${JSON.stringify(sortObject(document), null, 2)}\n`

if (mode === 'update') {
  await mkdir(dirname(snapshotPath), { recursive: true })
  await writeFile(snapshotPath, normalized, 'utf8')
  process.stdout.write(`Updated ${snapshotPath}\n`)
} else {
  const expected = await readFile(snapshotPath, 'utf8')
  if (expected !== normalized) {
    throw new Error('OpenAPI snapshot drift detected; review the API and update the snapshot explicitly')
  }
  process.stdout.write('OpenAPI snapshot is current and all local references resolve\n')
}

function validateDocument(root) {
  if (typeof root?.openapi !== 'string' || !root.openapi.startsWith('3.')) {
    throw new Error('Expected an OpenAPI 3 document')
  }

  walk(root, (value, path) => {
    if (typeof value?.$ref === 'string') {
      resolveReference(root, value.$ref, path)
    }
  })

  for (const [schemaName, schema] of Object.entries(root.components?.schemas || {})) {
    walk(schema, (value, path) => {
      if (!value?.properties || typeof value.properties !== 'object') return
      for (const [propertyName, propertySchema] of Object.entries(value.properties)) {
        if (/password/i.test(propertyName) && propertySchema?.writeOnly !== true) {
          throw new Error(
            `Password property ${schemaName}.${path.concat(propertyName).join('.')} must be writeOnly`,
          )
        }
      }
    })
  }
}

function resolveReference(root, reference, path) {
  if (!reference.startsWith('#/')) {
    throw new Error(`External OpenAPI reference is not allowed at ${path.join('.')}: ${reference}`)
  }
  let current = root
  for (const encoded of reference.slice(2).split('/')) {
    const key = encoded.replaceAll('~1', '/').replaceAll('~0', '~')
    if (!current || !Object.hasOwn(current, key)) {
      throw new Error(`Unresolved OpenAPI reference at ${path.join('.')}: ${reference}`)
    }
    current = current[key]
  }
}

function walk(value, visitor, path = []) {
  if (!value || typeof value !== 'object') return
  visitor(value, path)
  if (Array.isArray(value)) {
    value.forEach((item, index) => walk(item, visitor, path.concat(String(index))))
    return
  }
  for (const [key, child] of Object.entries(value)) {
    walk(child, visitor, path.concat(key))
  }
}

function sortObject(value) {
  if (Array.isArray(value)) return value.map(sortObject)
  if (!value || typeof value !== 'object') return value
  return Object.fromEntries(
    Object.keys(value)
      .sort((left, right) => left.localeCompare(right))
      .map((key) => [key, sortObject(value[key])]),
  )
}
