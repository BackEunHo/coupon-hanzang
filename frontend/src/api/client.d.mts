export class ApiError extends Error { status: number; code: string; requestId: string; constructor(status: number, data?: { code?: string; message?: string; requestId?: string }) }
export type RequestOptions = { method?: string; body?: unknown; key?: string; signal?: AbortSignal }
export function createApiClient(options?: { base?: string; fetcher?: typeof fetch; onUnauthorized?: () => void }): <T>(path: string, options?: RequestOptions) => Promise<T>
export function errorText(error: unknown): string
