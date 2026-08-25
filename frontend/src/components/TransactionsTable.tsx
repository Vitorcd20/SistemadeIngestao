import { useEffect } from 'react'
import { useTransactionsStore } from '../store/transactionsStore'

const currencyFormatter = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })

const SKELETON_WIDTHS: [string, string, string, string][] = [
  ['72px', '90px',  '200px', '64px'],
  ['80px', '75px',  '160px', '72px'],
  ['68px', '110px', '220px', '60px'],
  ['76px', '85px',  '180px', '68px'],
  ['70px', '95px',  '140px', '76px'],
  ['74px', '80px',  '190px', '64px'],
  ['78px', '100px', '170px', '72px'],
]

function SkeletonRows({ count }: { count: number }) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => {
        const [w0, w1, w2, w3] = SKELETON_WIDTHS[i % SKELETON_WIDTHS.length]
        return (
          <tr key={i}>
            <td><span className="skeleton-cell" style={{ width: w0 }} /></td>
            <td><span className="skeleton-cell" style={{ width: w1 }} /></td>
            <td><span className="skeleton-cell" style={{ width: w2 }} /></td>
            <td><span className="skeleton-cell" style={{ width: w3 }} /></td>
          </tr>
        )
      })}
    </>
  )
}

export function TransactionsTable() {
  const { items, index, hasMore, loading, error, init, goNext, goPrev } = useTransactionsStore()

  useEffect(() => {
    init()
  }, [init])

  const isFirstLoad = loading && items.length === 0
  const currentPage = index + 1

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
        <tbody className={loading && !isFirstLoad ? 'is-loading' : undefined}>
          {isFirstLoad ? (
            <SkeletonRows count={10} />
          ) : (
            <>
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
            </>
          )}
        </tbody>
      </table>

      {error && <p className="error-text" style={{ padding: '0 16px' }}>{error}</p>}

      <div className="table-pagination">
        <button className="btn-secondary" onClick={goPrev} disabled={index === 0 || loading}>
          ← Anterior
        </button>
        <span className="table-page-indicator">
          {!hasMore && currentPage > 1
            ? `Página ${currentPage} de ${currentPage}`
            : `Página ${currentPage}`}
        </span>
        <button className="btn-secondary" onClick={goNext} disabled={!hasMore || loading}>
          Próxima →
        </button>
      </div>
    </div>
  )
}
