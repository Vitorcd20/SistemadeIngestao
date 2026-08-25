import type { Summary } from '../api/client'

const currencyFormatter = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 0,
})

function compactNumber(value: number): string {
  return new Intl.NumberFormat('en-US', { notation: 'compact' }).format(value)
}

interface MetricCardsProps {
  summary: Summary | null
}

export function MetricCards({ summary }: MetricCardsProps) {
  if (!summary) return null

  const cards = [
    { label: 'Total de transações', value: compactNumber(summary.totalTransactions) },
    { label: 'Volume líquido', value: currencyFormatter.format(summary.totalVolume) },
    { label: 'Categorias', value: summary.distinctCategories },
    {
      label: 'Período',
      value: `${summary.earliestDate} → ${summary.latestDate}`,
      small: true,
    },
  ]

  return (
    <div className="metric-cards">
      {cards.map((card) => (
        <div className="metric-card" key={card.label}>
          <div className="metric-value" style={card.small ? { fontSize: '1rem' } : undefined}>
            {card.value}
          </div>
          <div className="metric-label">{card.label}</div>
        </div>
      ))}
    </div>
  )
}
