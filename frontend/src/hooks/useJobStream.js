import { useEffect } from 'react'
import { subscribeToJobEvents } from '../api/client'
import { useUploadStore } from '../store/uploadStore'

export function useJobStream(jobId) {
  const applyJobStatus = useUploadStore((s) => s.applyJobStatus)

  useEffect(() => {
    if (!jobId) return undefined

    const unsubscribe = subscribeToJobEvents(jobId, {
      onStatus: applyJobStatus,
      onError: () => {},
    })

    return unsubscribe
  }, [jobId, applyJobStatus])
}
