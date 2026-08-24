import { create } from 'zustand'
import { toast } from 'sonner'
import { getCurrentUser, login, logout, register } from '../api/client'

export const useAuthStore = create((set) => ({
  user: null,
  status: 'idle',
  error: null,

  async checkSession() {
    set({ status: 'checking', error: null })
    try {
      const user = await getCurrentUser()
      set({ user, status: 'authenticated' })
    } catch {
      set({ user: null, status: 'anonymous' })
    }
  },

  async login(username, password) {
    set({ error: null })
    try {
      const user = await login(username, password)
      set({ user, status: 'authenticated' })
      toast.success(`Login realizado como ${user.username}`)
      return true
    } catch (e) {
      set({ error: e.message })
      toast.error(e.message)
      return false
    }
  },

  async register(username, password) {
    set({ error: null })
    try {
      const user = await register(username, password)
      set({ user, status: 'authenticated' })
      toast.success(`Conta criada para ${user.username}`)
      return true
    } catch (e) {
      set({ error: e.message })
      toast.error(e.message)
      return false
    }
  },

  async logout() {
    try {
      await logout()
    } finally {
      set({ user: null, status: 'anonymous' })
    }
  },
}))
