import { createReadStream } from 'node:fs'
import { access, readFile, stat } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { createServer } from 'node:http'
import { extname, join, resolve, sep } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const websiteRoot = resolve(process.env.WEBSITE_ROOT || join(root, 'website', 'out'))
const releasesRoot = join(import.meta.dirname, 'releases')
const releasesFile = join(import.meta.dirname, 'releases.json')
const port = Number(process.env.PORT) || 9000
const host = process.env.HOST || '127.0.0.1'
const mimeTypes = { '.css': 'text/css; charset=utf-8', '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.json': 'application/json; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml' }

async function releases() {
  const data = JSON.parse(await readFile(releasesFile, 'utf8'))
  return data.map((release) => ({ ...release, file: `eyeye-client-${release.version}.jar` }))
}

async function releaseDetails(version) {
  const release = (await releases()).find((item) => item.version === version)
  if (!release) return null

  try {
    const file = join(releasesRoot, release.file)
    const sha256 = createHash('sha256').update(await readFile(file)).digest('hex')
    return { ...release, sha256 }
  } catch {
    return null
  }
}

function sendJson(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(body))
}

function safeFile(rootPath, path) {
  const file = resolve(rootPath, `.${path}`)
  return file === rootPath || file.startsWith(`${rootPath}${sep}`) ? file : null
}

async function sendStatic(response, pathname) {
  const requested = /^\/versions\/[^/]+\/?$/.test(pathname) ? '/versions/' : pathname
  let file = safeFile(websiteRoot, decodeURIComponent(requested))
  if (!file) return response.writeHead(403).end('Forbidden')
  try {
    if ((await stat(file)).isDirectory()) file = join(file, 'index.html')
    const body = await readFile(file)
    response.writeHead(200, { 'Content-Type': mimeTypes[extname(file)] || 'application/octet-stream', 'Cache-Control': extname(file) === '.html' ? 'no-cache' : 'public, max-age=31536000, immutable' })
    response.end(body)
  } catch {
    try {
      response.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' }).end(await readFile(join(websiteRoot, '404.html')))
    } catch { response.writeHead(404).end('Not found') }
  }
}

createServer(async (request, response) => {
  try {
    const url = new URL(request.url || '/', 'http://localhost')
    if (url.pathname === '/api/versions') return sendJson(response, 200, { releases: await releases() })
    if (url.pathname.startsWith('/api/versions/')) {
      const release = await releaseDetails(decodeURIComponent(url.pathname.slice('/api/versions/'.length)))
      return release ? sendJson(response, 200, release) : sendJson(response, 404, { error: 'Release not found' })
    }
    if (url.pathname.startsWith('/releases/')) {
      const release = (await releases()).find((item) => `/releases/${item.file}` === url.pathname)
      if (!release) return response.writeHead(404).end('Release not found')
      const file = join(releasesRoot, release.file)
      try { await access(file) } catch { return response.writeHead(404).end('Release file not found') }
      response.writeHead(200, { 'Content-Type': 'application/java-archive', 'Content-Disposition': `attachment; filename="${release.file}"`, 'Cache-Control': 'no-store' })
      createReadStream(file).pipe(response)
      return
    }
    await sendStatic(response, url.pathname)
  } catch {
    response.writeHead(500).end('Internal server error')
  }
}).listen(port, host, () => console.log(`EyEye server is running on http://${host}:${port}`))
