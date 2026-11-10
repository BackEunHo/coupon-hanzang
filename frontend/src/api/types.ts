export type Expiry = { mode: 'FIXED_DATE'; startDate: string; date: string } | { mode: 'RELATIVE_DAYS'; durationDays: number }
export type CouponFields = { title: string; benefit: string; notice: string; expiry: Expiry }
export type Template = CouponFields & { id: string; createdAt: string; updatedAt: string }
export type Page<T> = { items: T[]; page: { page: number; size: number; totalElements: number; totalPages: number } }
export type CouponStatus = 'PENDING_SEND' | 'ACTIVE' | 'SEND_FAILED' | 'REDEEMED' | 'EXPIRED' | 'CLOSED_INACTIVE'
export type MessageStatus = 'QUEUED' | 'SUBMITTED' | 'DELIVERED' | 'FAILED' | 'UNKNOWN'
export type MessageType = 'SMS' | 'LMS'
export type Coupon = Omit<CouponFields, 'expiry'> & { id: string; templateId: string; displayPhone: string; customerDataDeleted: boolean; issuedAt: string; startsAt: string; expiresAt: string; redeemedAt?: string | null; status: CouponStatus; issueMessageStatus: MessageStatus; closureReason?: string | null; expectedMessageType?: MessageType | null; messageByteLength?: number | null; actualMessageType?: MessageType | null }
export type IssueRequest = { templateId: string; recipientPhone: string; consentConfirmed: true; overrides?: Partial<CouponFields> }
export type CouponFilters = { phone: string; title: string; status: string }
export type MessagePreviewRequest = { templateId: string; overrides?: Partial<CouponFields> }
export type MessagePreviewResponse = { previewText: string; byteLength: number | null; expectedMessageType: MessageType | null; sendable: boolean; validationErrors: string[]; calculatedAt: string }
export type DeliveryRetryResponse = { id: string; kind: 'COUPON_ISSUED'; status: string; requestedAt: string }

export type RedeemRequest = { code: string }
