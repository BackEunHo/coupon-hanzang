import { createApiClient } from './client.mjs'
import type { Coupon, RedeemRequest, CouponFields, CouponFilters, DeliveryRetryResponse, IssueRequest, MessagePreviewRequest, MessagePreviewResponse, Page, Template } from './types'
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
  redeemCoupon: async (id: string, body: RedeemRequest, key: string): Promise<Coupon> => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 15_000)
    try { return await request<Coupon>(`/api/v1/coupons/${encodeURIComponent(id)}/redeem`, { method: 'POST', body, key, signal: controller.signal }) }
    finally { clearTimeout(timer) }
  },
  templates: (page: number, signal?: AbortSignal) => request<Page<Template>>(`/api/v1/templates?${query(page)}`, { signal }),
  template: (id: string, signal?: AbortSignal) => request<Template>(`/api/v1/templates/${encodeURIComponent(id)}`, { signal }),
  saveTemplate: (fields: Partial<CouponFields>, id?: string) => request<Template>(`/api/v1/templates${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PATCH' : 'POST', body: fields }),
  deleteTemplate: (id: string) => request<void>(`/api/v1/templates/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  coupons: (page: number, filters: CouponFilters, signal?: AbortSignal) => request<Page<Coupon>>(`/api/v1/coupons?${query(page, filters)}`, { signal }),
  coupon: (id: string, signal?: AbortSignal) => request<Coupon>(`/api/v1/coupons/${encodeURIComponent(id)}`, { signal }),
  issue: (body: IssueRequest, key: string) => request<Coupon>('/api/v1/coupons', { method: 'POST', body, key }),
  preview: (body: MessagePreviewRequest, signal?: AbortSignal) => request<MessagePreviewResponse>('/api/v1/coupons/preview', { method: 'POST', body, signal }),
  retryDelivery: (couponId: string, key: string) => request<DeliveryRetryResponse>(`/api/v1/coupons/${encodeURIComponent(couponId)}/delivery-retry`, { method: 'POST', key }),
}
