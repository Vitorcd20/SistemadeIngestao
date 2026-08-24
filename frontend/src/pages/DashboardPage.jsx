import { useEffect } from 'react'
import { useDashboardStore } from '../store/dashboardStore'
import { MetricCards } from '../components/MetricCards'
import { CategoryChart } from '../components/CategoryChart'
import { MonthlyChart } from '../components/MonthlyChart'
import { TransactionsTable } from '../components/TransactionsTable'

export function DashboardPage() {
  const { summary, categoryTotals, monthlyTotals, loading, error, load } = useDashboardStore()

  useEffect(() => {
    load()
  }, [])

  return (
    <div className="page">
      <h1>Painel</h1>
      <p className="subtitle">Visão consolidada de todas as transações importadas.</p>

      {error && <p className="error-text">{error}</p>}
      {loading && !summary && <p className="subtitle">Carregando…</p>}

      <MetricCards summary={summary} />

      {categoryTotals.length > 0 && (
        <div className="chart-grid">
          <CategoryChart data={categoryTotals} />
          <MonthlyChart data={monthlyTotals} />
        </div>
      )}

      <TransactionsTable />
    </div>
  )
}
