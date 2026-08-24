import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { useUploadStore } from '../store/uploadStore'
import { useJobStream } from '../hooks/useJobStream'
import { ProgressPanel } from '../components/ProgressPanel'

export function UploadPage() {
  const inputRef = useRef(null)
  const state = useUploadStore()
  const { jobId, status, fileName, startUpload, reset } = state

  useJobStream(status === 'processing' ? jobId : null)

  const handleFileChange = (e) => {
    const file = e.target.files?.[0]
    if (file) startUpload(file)
    e.target.value = ''
  }

  const isBusy = status === 'uploading' || status === 'processing'

  return (
    <div className="page">
      <h1>Importar CSV de transações</h1>
      <p className="subtitle">
        Envie um CSV de transações financeiras (id, data, categoria, valor, descrição).
        O arquivo é processado em segundo plano — esta página é atualizada em tempo real via SSE.
      </p>

      <div className="upload-controls">
        <button
          className="btn-primary"
          disabled={isBusy}
          onClick={() => inputRef.current?.click()}
        >
          {isBusy ? 'Em andamento…' : 'Selecionar arquivo CSV'}
        </button>
        <input
          ref={inputRef}
          type="file"
          accept=".csv,text/csv"
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
        {status !== 'idle' && !isBusy && (
          <button className="btn-secondary" onClick={reset}>
            Enviar outro arquivo
          </button>
        )}
      </div>

      {status !== 'idle' && (
        <ProgressPanel
          fileName={fileName}
          status={status}
          rowsProcessed={state.rowsProcessed}
          rowsFailed={state.rowsFailed}
          errorSample={state.errorSample}
          uploadError={state.uploadError}
        />
      )}

      {status === 'completed' && (
        <Link className="btn-primary dashboard-link" to="/dashboard">
          Ver painel →
        </Link>
      )}
    </div>
  )
}
