import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../../api'
import type { Coupon } from '../../api/types'
import { createDeliveryTracker, type DeliveryState } from './message-state.mjs'

export function useCouponDelivery(coupon: Coupon) {
  const [state, setState] = useState<DeliveryState>({ data: coupon, loading: true, autoStopped: false })
  const tracker = useRef<ReturnType<typeof createDeliveryTracker> | undefined>(undefined)
  useEffect(() => {
    const current = createDeliveryTracker(coupon, api.coupon, setState)
    tracker.current = current
    const visible = () => current.setVisible(document.visibilityState !== 'hidden')
    document.addEventListener('visibilitychange', visible)
    if (document.visibilityState === 'hidden') current.setVisible(false)
    void current.start()
    return () => { current.stop(); document.removeEventListener('visibilitychange', visible); tracker.current = undefined }
  }, [coupon.id])
  const refresh = useCallback(() => tracker.current?.refresh(), [])
  return { ...state, refresh }
}
