const STATUS_LABELS = {
  uploading: 'Enviando…',
  processing: 'Processando…',
  completed: 'Concluído',
  failed: 'Falhou',
}

export function ProgressPanel({ fileName, status, rowsProcessed, rowsFailed, errorSample, uploadError }) {
  const isActive = status === 'uploading' || status === 'processing'

  return (
    <div className="panel">
      <div className="panel-header">
        <span className="file-name">{fileName}</span>
        <span className={`status-badge status-${status}`}>{STATUS_LABELS[status] ?? status}</span>
      </div>

      {isActive && (
        <div className="progress-bar">
          <div className="progress-bar-fill indeterminate" />
        </div>
      )}

      <div className="stat-row">
        <div className="stat">
          <span className="stat-value">{rowsProcessed.toLocaleString()}</span>
          <span className="stat-label">linhas processadas</span>
        </div>
        <div className="stat">
          <span className="stat-value">{rowsFailed.toLocaleString()}</span>
          <span className="stat-label">linhas com falha</span>
        </div>
      </div>

      {uploadError && <p className="error-text">{uploadError}</p>}

      {errorSample.length > 0 && (
        <details className="error-details">
          <summary>
            {errorSample.length} {errorSample.length > 1 ? 'exemplos de erro' : 'exemplo de erro'}
          </summary>
          <ul>
            {errorSample.map((line, i) => (
              <li key={i}>{line}</li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
