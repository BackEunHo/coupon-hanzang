import { test } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { createRequire } from 'node:module'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

test('public header replaces desktop and mobile authentication links after login', async () => {
  const result = await build({ stdin: { contents: `import React from 'react'; import {renderToStaticMarkup} from 'react-dom/server'; import {Header} from './src/App'; module.exports = (until) => { globalThis.sessionStorage = { getItem: () => until }; return renderToStaticMarkup(React.createElement(Header)); };`, resolveDir: process.cwd(), loader: 'tsx' }, bundle: true, platform: 'node', format: 'cjs', write: false, loader: { '.css': 'empty' }, define: { 'import.meta.env': '{}' } })
  const dir = await mkdtemp(join(tmpdir(), 'coupon-header-'))
  try {
    const file = join(dir, 'render.cjs')
    await writeFile(file, result.outputFiles[0].text)
    const render = createRequire(import.meta.url)(file)
    const signedIn = render(String(Date.now() + 60_000))
    assert.doesNotMatch(signedIn, /href="\/(login|signup)\//)
    assert.equal((signedIn.match(/>로그아웃<\/button>/g) || []).length, 2)
    const signedOut = render(null)
    assert.equal((signedOut.match(/href="\/login\//g) || []).length, 2)
    assert.equal((signedOut.match(/href="\/signup\//g) || []).length, 2)
    assert.doesNotMatch(signedOut, /로그아웃/)
    assert.doesNotMatch(render('1'), /로그아웃/)
  } finally { delete globalThis.sessionStorage; await rm(dir, { recursive: true, force: true }) }
})
