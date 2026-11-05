import { ApiError } from '../api/client.mjs'

export const signedInKey = 'couponhanjang-signed-in-until'
export const sessionChangedEvent = 'couponhanjang-session-changed'
const browserStorage = () => typeof sessionStorage === 'undefined' ? undefined : sessionStorage
function notify() {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event(sessionChangedEvent))
}
export function isSignedIn(storage = browserStorage(), now = Date.now()) {
  return Number(storage?.getItem(signedInKey)) > now
}
export function markSignedIn(storage = browserStorage(), now = Date.now()) {
  storage?.setItem(signedInKey, String(now + 24 * 60 * 60 * 1000))
  notify()
}
export function clearSignedIn(storage = browserStorage()) {
  storage?.removeItem(signedInKey)
  notify()
}
export async function logoutSession(request, storage = browserStorage()) {
  try { await request('/api/v1/auth/logout', { method: 'POST' }) }
  catch (error) { if (!(error instanceof ApiError) || error.status !== 401) throw error }
  clearSignedIn(storage)
}
