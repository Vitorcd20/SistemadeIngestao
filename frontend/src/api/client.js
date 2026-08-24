import { translateErrorMessage } from '../utils/errorMessages'

const BASE = '/api'

function csrfToken() {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function request(path, options = {}) {
  const headers = { ...options.headers }
  const method = (options.method ?? 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let res
  try {
    res = await fetch(`${BASE}${path}`, { ...options, headers, credentials: 'include' })
  } catch {
    throw new Error(translateErrorMessage(null))
  }

  if (!res.ok) {
    let rawMessage = null
    try {
      const body = await res.json()
      rawMessage = body.message
    } catch {
    }
    throw new Error(translateErrorMessage(rawMessage))
  }
  return res.status === 204 ? null : res.json()
}

function requestJson(path, method, body) {
  return request(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function register(username, password) {
  return requestJson('/auth/register', 'POST', { username, password })
}

export function login(username, password) {
  return requestJson('/auth/login', 'POST', { username, password })
}

export function logout() {
  return request('/auth/logout', { method: 'POST' })
}

export function getCurrentUser() {
  return request('/auth/me')
}

export function uploadCsv(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request('/uploads', { method: 'POST', body: formData })
}

export function getJobStatus(jobId) {
  return request(`/jobs/${jobId}`)
}

export function subscribeToJobEvents(jobId, { onStatus, onError }) {
  const source = new EventSource(`${BASE}/jobs/${jobId}/events`, { withCredentials: true })

  source.addEventListener('status', (event) => {
    try {
      onStatus(JSON.parse(event.data))
    } catch (e) {
      onError?.(e)
    }
  })

  source.onerror = (event) => {
    onError?.(event)
  }

  return () => source.close()
}

export function listTransactions({ cursor, limit = 50 } = {}) {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  return request(`/transactions?${params.toString()}`)
}

export function getAggregationsByCategoryMonth({ from, to } = {}) {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return request(`/aggregations/by-category-month${query ? `?${query}` : ''}`)
}

export function getSummary() {
  return request('/aggregations/summary')
}
