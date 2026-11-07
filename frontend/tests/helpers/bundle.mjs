import { build } from 'esbuild'
import { createRequire } from 'node:module'
import { mkdtemp, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

export async function bundleModule(contents, run) {
  const output = await build({ stdin: { contents, resolveDir: process.cwd(), loader: 'tsx' }, bundle: true, platform: 'node', format: 'cjs', write: false, loader: { '.css': 'empty' }, define: { 'import.meta.env': '{}' } })
  const dir = await mkdtemp(join(tmpdir(), 'coupon-test-'))
  try {
    const file = join(dir, 'module.cjs')
    await writeFile(file, output.outputFiles[0].text)
    return await run(createRequire(import.meta.url)(file))
  } finally { await rm(dir, { recursive: true, force: true }) }
}
