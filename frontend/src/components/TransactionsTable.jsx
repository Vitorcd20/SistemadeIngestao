import { useEffect } from 'react'
import { useTransactionsStore } from '../store/transactionsStore'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

export function TransactionsTable() {
  const { items, index, hasMore, loading, error, init, goNext, goPrev } = useTransactionsStore()

  useEffect(() => {
    init()
  }, [])

  return (
    <div className="table-panel">
      <table>
        <thead>
          <tr>
            <th>Data</th>
            <th>Categoria</th>
            <th>Descrição</th>
            <th>Valor</th>
          </tr>
        </thead>
        <tbody>
          {items.map((row) => (
            <tr key={row.id}>
              <td>{row.transactionDate}</td>
              <td>{row.category}</td>
              <td>{row.description}</td>
              <td className={row.amount < 0 ? 'amount-negative' : 'amount-positive'}>
                {currencyFormatter.format(row.amount)}
              </td>
            </tr>
          ))}
          {!loading && items.length === 0 && !error && (
            <tr>
              <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                Nenhuma transação ainda.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      {error && <p className="error-text" style={{ padding: '0 16px' }}>{error}</p>}

      <div className="table-pagination">
        <button className="btn-secondary" onClick={goPrev} disabled={index === 0 || loading}>
          ← Anterior
        </button>
        <button className="btn-secondary" onClick={goNext} disabled={!hasMore || loading}>
          Próxima →
        </button>
      </div>
    </div>
  )
}
