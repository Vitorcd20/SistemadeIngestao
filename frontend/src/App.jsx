import { useEffect } from 'react'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { Toaster } from 'sonner'
import { UploadPage } from './pages/UploadPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { useAuthStore } from './store/authStore'

function RequireAuth({ children }) {
  const status = useAuthStore((s) => s.status)
  if (status === 'idle' || status === 'checking') return null
  if (status === 'anonymous') return <Navigate to="/login" replace />
  return children
}

function SessionInfo() {
  const { user, logout } = useAuthStore()
  if (!user) return null
  return (
    <div className="session-info">
      <span className="username">{user.username}</span>
      <button className="btn-secondary" onClick={logout}>
        Sair
      </button>
    </div>
  )
}

function App() {
  const checkSession = useAuthStore((s) => s.checkSession)

  useEffect(() => {
    checkSession()
  }, [checkSession])

  return (
    <div className="app-shell">
      <Toaster
        theme="dark"
        richColors
        toastOptions={{ style: { borderRadius: 'var(--radius)' } }}
        style={{
          '--normal-bg': 'var(--surface)',
          '--normal-border': 'var(--border)',
          '--normal-text': 'var(--text)',
          '--success-bg': 'var(--success-tint)',
          '--success-border': 'var(--success)',
          '--success-text': 'var(--success)',
          '--error-bg': 'var(--danger-tint)',
          '--error-border': 'var(--danger)',
          '--error-text': 'var(--danger)',
        }}
      />
      <nav className="top-nav">
        <span className="brand">Sistema de Ingestão</span>
        <div className="nav-links">
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
            Importar
          </NavLink>
          <NavLink to="/dashboard" className={({ isActive }) => (isActive ? 'active' : '')}>
            Painel
          </NavLink>
        </div>
        <SessionInfo />
      </nav>

      <main>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/"
            element={
              <RequireAuth>
                <UploadPage />
              </RequireAuth>
            }
          />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <DashboardPage />
              </RequireAuth>
            }
          />
          <Route
            path="*"
            element={
              <RequireAuth>
                <NotFoundPage />
              </RequireAuth>
            }
          />
        </Routes>
      </main>
    </div>
  )
}

export default App
