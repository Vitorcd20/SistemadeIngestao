import { create } from 'zustand'
import { toast } from 'sonner'
import { getAggregationsByCategoryMonth, getSummary } from '../api/client'
import type { Summary } from '../api/client'

export interface CategoryTotal {
  category: string
  total: number
}

export interface MonthlyTotal {
  month: string
  total: number
}

interface DashboardState {
  summary: Summary | null
  categoryTotals: CategoryTotal[]
  monthlyTotals: MonthlyTotal[]
  loading: boolean
  error: string | null
  load: () => Promise<void>
}

export const useDashboardStore = create<DashboardState>((set) => ({
  summary: null,
  categoryTotals: [],
  monthlyTotals: [],
  loading: false,
  error: null,

  async load() {
    set({ loading: true, error: null })
    try {
      const [summary, byCategoryMonth] = await Promise.all([
        getSummary(),
        getAggregationsByCategoryMonth(),
      ])

      const categoryMap = new Map<string, number>()
      const monthMap = new Map<string, number>()
      for (const row of byCategoryMonth) {
        categoryMap.set(row.category, (categoryMap.get(row.category) ?? 0) + Number(row.totalAmount))
        monthMap.set(row.month, (monthMap.get(row.month) ?? 0) + Number(row.totalAmount))
      }

      set({
        summary,
        categoryTotals: Array.from(categoryMap, ([category, total]) => ({ category, total })),
        monthlyTotals: Array.from(monthMap, ([month, total]) => ({ month, total })).sort((a, b) =>
          a.month.localeCompare(b.month),
        ),
        loading: false,
      })
    } catch (e) {
      const msg = (e as Error).message
      set({ error: msg, loading: false })
      toast.error(msg)
    }
  },
}))
