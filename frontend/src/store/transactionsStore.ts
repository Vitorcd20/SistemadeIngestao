import { create } from 'zustand'
import { toast } from 'sonner'
import { listTransactions } from '../api/client'
import type { Transaction } from '../api/client'

const PAGE_SIZE = 25

interface TransactionsState {
  items: Transaction[]
  history: (string | null)[]
  index: number
  frontierCursor: string | null
  hasMore: boolean
  loading: boolean
  error: string | null
  fetchPage: (cursor: string | null) => Promise<void>
  init: () => void
  goNext: () => void
  goPrev: () => void
}

export const useTransactionsStore = create<TransactionsState>((set, get) => ({
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
      const msg = (e as Error).message
      set({ error: msg, loading: false })
      toast.error(msg)
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
