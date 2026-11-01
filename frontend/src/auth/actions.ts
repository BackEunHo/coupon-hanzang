import { createApiClient } from '../api/client.mjs'
import { logoutSession } from './session.mjs'

const request = createApiClient({ base: import.meta.env.VITE_API_BASE_URL || '' })
export const signOut = () => logoutSession(request)
