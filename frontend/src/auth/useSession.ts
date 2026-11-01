import { useEffect, useState } from 'react'
import { isSignedIn, sessionChangedEvent, signedInKey } from './session.mjs'

export function useSession() {
  const [signedIn, setSignedIn] = useState(() => isSignedIn())
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined
    function refresh() {
      clearTimeout(timer)
      setSignedIn(isSignedIn())
      const remaining = Number(sessionStorage.getItem(signedInKey)) - Date.now()
      if (remaining > 0) timer = setTimeout(refresh, Math.min(remaining + 1, 2_147_483_647))
    }
    const events = ['focus', 'pageshow', 'storage', sessionChangedEvent]
    events.forEach(event => window.addEventListener(event, refresh))
    refresh()
    return () => { clearTimeout(timer); events.forEach(event => window.removeEventListener(event, refresh)) }
  }, [])
  return signedIn
}
