import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="page">
      <h1>Página não encontrada</h1>
      <p className="subtitle">A página que você procura não existe.</p>
      <Link className="btn-primary" to="/">
        Voltar para importação
      </Link>
    </div>
  )
}
