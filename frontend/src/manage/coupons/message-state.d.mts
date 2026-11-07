import type { Coupon, CouponFields, DeliveryRetryResponse, MessagePreviewRequest, MessagePreviewResponse, Template } from '../../api/types'
export type PreviewState = { key: string | null; loading: boolean; data?: MessagePreviewResponse; error?: unknown }
export type DeliveryState = { data: Coupon; loading: boolean; autoStopped: boolean; error?: unknown }
export function previewRequest(template: Template, fields: CouponFields): MessagePreviewRequest
export function createPreviewController(request: (body: MessagePreviewRequest, signal: AbortSignal) => Promise<MessagePreviewResponse>, onChange: (state: PreviewState) => void): { cancel: () => void; update: (body: MessagePreviewRequest | null) => void; refresh: (body: MessagePreviewRequest) => Promise<MessagePreviewResponse | undefined> }
export function createDeliveryTracker(initial: Coupon, request: (id: string, signal: AbortSignal) => Promise<Coupon>, onChange: (state: DeliveryState) => void): { start: () => Promise<Coupon | undefined>; refresh: () => Promise<Coupon | undefined>; setVisible: (visible: boolean) => void; stop: () => void }
export function canRetryDelivery(coupon: Coupon, now?: number): boolean
export function createDeliveryRetryAttempt(id: string, send: (id: string, key: string) => Promise<DeliveryRetryResponse>, keyFactory?: () => string): { send: () => Promise<DeliveryRetryResponse> }
