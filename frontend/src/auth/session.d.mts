type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
export const signedInKey: string
export const sessionChangedEvent: string
export function isSignedIn(storage?: SessionStorage, now?: number): boolean
export function markSignedIn(storage?: SessionStorage, now?: number): void
export function clearSignedIn(storage?: SessionStorage): void
export function logoutSession(request: (path: string, options: { method: string }) => Promise<unknown>, storage?: SessionStorage): Promise<void>
