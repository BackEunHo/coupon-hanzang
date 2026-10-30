import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createViteServer } from 'vite'

 test('development API requests reach the backend with their original origin', async () => {
  const backend = createHttpServer((req, res) => {
    res.writeHead(400, { 'Content-Type': 'application/json', 'Set-Cookie': 'SESSION=test; HttpOnly' })
    res.end(JSON.stringify({ url: req.url, origin: req.headers.origin }))
  })
  await new Promise(resolve => backend.listen(0, '127.0.0.1', resolve))
  process.env.API_PROXY_TARGET = `http://127.0.0.1:${backend.address().port}`
  let vite
  try {
    vite = await createViteServer({ server: { host: '127.0.0.1', port: 0 }, logLevel: 'silent' })
    await vite.listen()
    const response = await fetch(`http://127.0.0.1:${vite.httpServer.address().port}/api/v1/auth/signup`, {
      method: 'POST', headers: { 'Content-Type': 'application/json', Origin: 'http://localhost:5173' }, body: '{}',
    })
    assert.equal(response.status, 400)
    assert.deepEqual(await response.json(), { url: '/api/v1/auth/signup', origin: 'http://localhost:5173' })
    assert.match(response.headers.get('set-cookie'), /SESSION=test/)
  } finally {
    if (vite) await vite.close()
    await new Promise(resolve => backend.close(resolve))
    delete process.env.API_PROXY_TARGET
  }
})
