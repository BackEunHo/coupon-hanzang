export class ApiError extends Error {
  constructor(status, data = {}) {
    super(data.message || '요청을 처리하지 못했습니다.')
    this.status = status
    this.code = data.code || ''
    this.requestId = data.requestId || ''
  }
}
export function createApiClient({ base = '', fetcher = fetch, onUnauthorized = () => {} } = {}) {
  return async function request(path, { method = 'GET', body, key, signal } = {}) {
    const headers = { Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (key) headers['Idempotency-Key'] = key
    const response = await fetcher(`${base.replace(/\/$/, '')}${path}`, { method, credentials: 'include', headers, body: body === undefined ? undefined : JSON.stringify(body), signal })
    if (!response.ok) {
      const data = await response.json().catch(() => ({}))
      if (response.status === 401) onUnauthorized()
      throw new ApiError(response.status, data)
    }
    if (response.status === 204) return undefined
    return response.json()
  }
}
export function errorText(error) {
  if (!(error instanceof ApiError)) return '서버와 연결하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.'
  if (error.code === 'QUOTA_EXCEEDED') return '이번 달 문자 발송 한도에 도달해 발급할 수 없습니다.'
  if (error.code.includes('IDEMPOTENCY')) return '이 요청과 기존 발급 내용이 다릅니다. 발급 목록에서 결과를 먼저 확인해 주세요.'
  if (error.status === 401) return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
  if (error.status === 403) return '요청이 허용되지 않았습니다. 접속 주소와 서버 설정을 확인해 주세요.'
  if (error.status === 404) return '항목을 찾을 수 없습니다. 삭제되었거나 접근할 수 없는 항목입니다.'
  if (error.status === 429) return '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.'
  return error.message
}
