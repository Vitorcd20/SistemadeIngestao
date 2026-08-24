import { create } from 'zustand'
import { toast } from 'sonner'
import { getAggregationsByCategoryMonth, getSummary } from '../api/client'

export const useDashboardStore = create((set) => ({
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

      const categoryMap = new Map()
      const monthMap = new Map()
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
      set({ error: e.message, loading: false })
      toast.error(e.message)
    }
  },
}))
