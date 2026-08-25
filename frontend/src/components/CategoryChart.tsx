import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { CategoryTotal } from '../store/dashboardStore'

const ACCENT = '#e4e4e7'
const GRID = '#2a2a2e'
const TEXT_MUTED = '#8b8b90'

interface TooltipInternalProps {
  active?: boolean
  payload?: Array<{ value: number }>
  label?: string
}

function CustomTooltip({ active, payload, label }: TooltipInternalProps) {
  if (!active || !payload?.length) return null
  const value = payload[0].value ?? 0
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{label}</div>
      <div className="chart-tooltip-value">
        {value.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })}
      </div>
    </div>
  )
}

function formatCompactSigned(v: number): string {
  const sign = v < 0 ? '-' : ''
  return `${sign}$${Math.round(Math.abs(v) / 1000)}k`
}

interface CategoryChartProps {
  data: CategoryTotal[]
}

export function CategoryChart({ data }: CategoryChartProps) {
  const sorted = [...data].sort((a, b) => b.total - a.total)

  return (
    <div className="chart-panel">
      <h3>Valor líquido por categoria</h3>
      <ResponsiveContainer width="100%" height={360}>
        <BarChart data={sorted} layout="vertical" margin={{ left: 8, right: 16 }}>
          <CartesianGrid horizontal={false} stroke={GRID} />
          <XAxis
            type="number"
            tickFormatter={formatCompactSigned}
            stroke={TEXT_MUTED}
            tick={{ fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <YAxis
            type="category"
            dataKey="category"
            width={100}
            stroke={TEXT_MUTED}
            tick={{ fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ fill: 'rgba(91,141,239,0.08)' }} />
          <Bar dataKey="total" fill={ACCENT} radius={4} maxBarSize={20} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}
