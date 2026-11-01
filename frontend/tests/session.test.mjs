import { test } from 'node:test'
import assert from 'node:assert/strict'
import { ApiError } from '../src/api/client.mjs'
import { isSignedIn, markSignedIn, clearSignedIn, logoutSession } from '../src/auth/session.mjs'

function storage() {
  const values = new Map()
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
}
test('login marker expires and logout removes it', () => {
  const store = storage()
  assert.equal(isSignedIn(store, 1000), false)
  markSignedIn(store, 1000)
  assert.equal(isSignedIn(store, 1001), true)
  assert.equal(isSignedIn(store, 1000 + 24 * 60 * 60 * 1000), false)
  clearSignedIn(store)
  assert.equal(isSignedIn(store, 1001), false)
})
test('logout clears login state on success or an already expired server session', async () => {
  for (const error of [null, new ApiError(401)]) {
    const store = storage()
    markSignedIn(store)
    await logoutSession(async (path, options) => {
      assert.equal(path, '/api/v1/auth/logout')
      assert.equal(options.method, 'POST')
      if (error) throw error
    }, store)
    assert.equal(isSignedIn(store), false)
  }
})
test('failed logout retains login state so the user can retry', async () => {
  const store = storage()
  markSignedIn(store)
  await assert.rejects(logoutSession(async () => { throw new ApiError(503) }, store))
  assert.equal(isSignedIn(store), true)
})
