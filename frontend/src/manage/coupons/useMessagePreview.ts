import { useEffect, useMemo, useState } from 'react'
import { api } from '../../api'
import type { MessagePreviewRequest } from '../../api/types'
import { createPreviewController, type PreviewState } from './message-state.mjs'

export function useMessagePreview(body: MessagePreviewRequest | null) {
  const key = body ? JSON.stringify(body) : null
  const [state, setState] = useState<PreviewState>({ key: null, loading: false })
  const controller = useMemo(() => createPreviewController(api.preview, setState), [])
  useEffect(() => {
    controller.update(key ? JSON.parse(key) : null)
    return controller.cancel
  }, [controller, key])
  const current = state.key === key ? state : { key, loading: Boolean(key) }
  return { ...current, refresh: () => body ? controller.refresh(body) : Promise.resolve(undefined) }
}
