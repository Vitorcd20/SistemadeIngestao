import { create } from 'zustand'
import { toast } from 'sonner'
import { listTransactions } from '../api/client'

const PAGE_SIZE = 25

export const useTransactionsStore = create((set, get) => ({
  items: [],
  history: [null],
  index: 0,
  frontierCursor: null,
  hasMore: false,
  loading: false,
  error: null,

  async fetchPage(cursor) {
    set({ loading: true, error: null })
    try {
      const response = await listTransactions({ cursor, limit: PAGE_SIZE })
      set({
        items: response.items,
        hasMore: response.hasMore,
        frontierCursor: response.nextCursor,
        loading: false,
      })
    } catch (e) {
      set({ error: e.message, loading: false })
      toast.error(e.message)
    }
  },

  init() {
    set({ history: [null], index: 0 })
    get().fetchPage(null)
  },

  goNext() {
    const { index, history, hasMore, frontierCursor } = get()
    if (index < history.length - 1) {
      const newIndex = index + 1
      set({ index: newIndex })
      get().fetchPage(history[newIndex])
    } else if (hasMore && frontierCursor) {
      const newHistory = [...history, frontierCursor]
      set({ history: newHistory, index: newHistory.length - 1 })
      get().fetchPage(frontierCursor)
    }
  },

  goPrev() {
    const { index, history } = get()
    if (index > 0) {
      const newIndex = index - 1
      set({ index: newIndex })
      get().fetchPage(history[newIndex])
    }
  },
}))
