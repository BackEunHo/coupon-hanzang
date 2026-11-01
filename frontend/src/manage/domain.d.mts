import type { CouponFields, CouponStatus, Expiry, IssueRequest, MessageStatus } from '../api/types'
export const couponStatuses: Record<CouponStatus, string>
export const messageStatuses: Record<MessageStatus, string>
export function validPhone(phone: string): boolean
export function validateFields(fields: CouponFields): string
export function changedFields(original: CouponFields, next: CouponFields): Partial<CouponFields>
export function issueBody(templateId: string, original: CouponFields, fields: CouponFields, phone: string): IssueRequest
export function displayDate(date: string): string
export function relativeEnd(days: number, now?: Date): string
export function expiryText(expiry: Expiry, now?: Date): string
export function periodText(start: string, end: string): string
export function timeText(value: string): string
export function createIssueAttempt<T>(body: IssueRequest, send: (body: IssueRequest, key: string) => Promise<T>, keyFactory?: () => string): { send: () => Promise<T> }
export function latestRequest(): { cancel: () => void; run: <T>(work: (signal: AbortSignal) => Promise<T>, apply: (value: T) => void) => Promise<void> }
