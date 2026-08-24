import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

const ACCENT = '#e4e4e7'
const GRID = '#2a2a2e'
const TEXT_MUTED = '#8b8b90'

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null
  const value = payload[0].value
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-label">{label}</div>
      <div className="chart-tooltip-value">
        {value.toLocaleString('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })}
      </div>
    </div>
  )
}

export function MonthlyChart({ data }) {
  return (
    <div className="chart-panel">
      <h3>Valor líquido por mês</h3>
      <ResponsiveContainer width="100%" height={360}>
        <LineChart data={data} margin={{ left: 8, right: 16 }}>
          <CartesianGrid vertical={false} stroke={GRID} />
          <XAxis
            dataKey="month"
            stroke={TEXT_MUTED}
            tick={{ fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            interval="preserveStartEnd"
          />
          <YAxis
            tickFormatter={(v) => `${v < 0 ? '-' : ''}$${Math.round(Math.abs(v) / 1000)}k`}
            stroke={TEXT_MUTED}
            tick={{ fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip content={<CustomTooltip />} cursor={{ stroke: GRID }} />
          <Line
            type="monotone"
            dataKey="total"
            stroke={ACCENT}
            strokeWidth={2}
            dot={{ r: 3, fill: ACCENT, stroke: '#141416', strokeWidth: 2 }}
            activeDot={{ r: 5, fill: ACCENT, stroke: '#141416', strokeWidth: 2 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
