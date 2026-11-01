import { createApiClient } from './client.mjs'
import type { Coupon, CouponFields, CouponFilters, IssueRequest, Page, Template } from './types'
import { clearSignedIn } from '../auth/session.mjs'

const request = createApiClient({
  base: import.meta.env.VITE_API_BASE_URL || '',
  onUnauthorized() {
    clearSignedIn()
    window.location.replace('/login/?returnTo=manage')
  },
})
const query = (page: number, filters: Partial<CouponFilters> = {}) => {
  const params = new URLSearchParams({ page: String(page), size: '20' })
  for (const [key, value] of Object.entries(filters)) if (value.trim()) params.set(key, value.trim())
  return params.toString()
}
export const api = {
  templates: (page: number, signal?: AbortSignal) => request<Page<Template>>(`/api/v1/templates?${query(page)}`, { signal }),
  template: (id: string, signal?: AbortSignal) => request<Template>(`/api/v1/templates/${encodeURIComponent(id)}`, { signal }),
  saveTemplate: (fields: Partial<CouponFields>, id?: string) => request<Template>(`/api/v1/templates${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PATCH' : 'POST', body: fields }),
  deleteTemplate: (id: string) => request<void>(`/api/v1/templates/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  coupons: (page: number, filters: CouponFilters, signal?: AbortSignal) => request<Page<Coupon>>(`/api/v1/coupons?${query(page, filters)}`, { signal }),
  coupon: (id: string, signal?: AbortSignal) => request<Coupon>(`/api/v1/coupons/${encodeURIComponent(id)}`, { signal }),
  issue: (body: IssueRequest, key: string) => request<Coupon>('/api/v1/coupons', { method: 'POST', body, key }),
}
