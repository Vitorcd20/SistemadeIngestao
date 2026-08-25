import { create } from 'zustand'
import { toast } from 'sonner'
import { getCurrentUser, login, logout, register } from '../api/client'
import type { User } from '../api/client'

type AuthStatus = 'idle' | 'checking' | 'authenticated' | 'anonymous'

interface AuthState {
  user: User | null
  status: AuthStatus
  error: string | null
  checkSession: () => Promise<void>
  login: (username: string, password: string) => Promise<boolean>
  register: (username: string, password: string) => Promise<boolean>
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set) => ({
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
      const msg = (e as Error).message
      set({ error: msg })
      toast.error(msg)
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
      const msg = (e as Error).message
      set({ error: msg })
      toast.error(msg)
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
