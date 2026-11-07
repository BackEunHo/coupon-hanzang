import { test } from 'node:test'
import assert from 'node:assert/strict'
import { build } from 'esbuild'
import { createRequire } from 'node:module'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

test('management entry renders search, issuing action and honest delivery scope', async () => {
  const result = await build({ stdin: { contents: `import React from 'react'; import { renderToStaticMarkup } from 'react-dom/server'; import { ManagementPage } from './src/manage/ManagementPage'; module.exports = renderToStaticMarkup(React.createElement(ManagementPage));`, resolveDir: process.cwd(), loader: 'tsx' }, bundle: true, platform: 'node', format: 'cjs', write: false, loader: { '.css': 'empty' }, define: { 'import.meta.env': '{}' } })
  const dir = await mkdtemp(join(tmpdir(), 'coupon-render-'))
  try {
    const file = join(dir, 'render.cjs')
    await writeFile(file, result.outputFiles[0].text)
    const html = createRequire(import.meta.url)(file)
    assert.match(html, /고객 휴대전화 번호/)
    assert.match(html, /쿠폰 발급/)
    assert.match(html, /문자.*전달 결과/)
    assert.match(html, /템플릿 관리/)
    assert.doesNotMatch(html, /실제 문자 발송.*지원하지 않습니다/)
  } finally { await rm(dir, { recursive: true, force: true }) }
})
