import { create } from 'zustand'
import { toast } from 'sonner'
import { uploadCsv } from '../api/client'
import type { JobStatus } from '../api/client'

export type UploadStatus = 'idle' | 'uploading' | 'processing' | 'completed' | 'failed'

interface UploadState {
  fileName: string | null
  jobId: string | null
  status: UploadStatus
  rowsProcessed: number
  rowsFailed: number
  errorSample: string[]
  uploadError: string | null
  startUpload: (file: File) => Promise<void>
  applyJobStatus: (job: JobStatus) => void
  reset: () => void
}

const initialState: Omit<UploadState, 'startUpload' | 'applyJobStatus' | 'reset'> = {
  fileName: null,
  jobId: null,
  status: 'idle',
  rowsProcessed: 0,
  rowsFailed: 0,
  errorSample: [],
  uploadError: null,
}

export const useUploadStore = create<UploadState>((set) => ({
  ...initialState,

  async startUpload(file) {
    set({ ...initialState, fileName: file.name, status: 'uploading' })
    try {
      const response = await uploadCsv(file)
      set({ jobId: response.jobId, status: 'processing' })
    } catch (e) {
      const msg = (e as Error).message
      set({ status: 'failed', uploadError: msg })
      toast.error(msg)
    }
  },

  applyJobStatus(job) {
    const status = job.status.toLowerCase() as UploadStatus
    set({
      rowsProcessed: job.rowsProcessed,
      rowsFailed: job.rowsFailed,
      errorSample: job.errorSample ?? [],
      status,
    })
    if (status === 'completed') {
      toast.success(`Importação concluída: ${job.rowsProcessed.toLocaleString()} linhas processadas`)
    } else if (status === 'failed') {
      toast.error('Falha na importação')
    }
  },

  reset() {
    set(initialState)
  },
}))
