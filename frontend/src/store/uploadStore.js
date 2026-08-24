import { create } from 'zustand'
import { toast } from 'sonner'
import { uploadCsv } from '../api/client'

const initialState = {
  fileName: null,
  jobId: null,
  status: 'idle',
  rowsProcessed: 0,
  rowsFailed: 0,
  errorSample: [],
  uploadError: null,
}

export const useUploadStore = create((set) => ({
  ...initialState,

  async startUpload(file) {
    set({ ...initialState, fileName: file.name, status: 'uploading' })
    try {
      const response = await uploadCsv(file)
      set({ jobId: response.jobId, status: 'processing' })
    } catch (e) {
      set({ status: 'failed', uploadError: e.message })
      toast.error(e.message)
    }
  },

  applyJobStatus(job) {
    const status = job.status.toLowerCase()
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
