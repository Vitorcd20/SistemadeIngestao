import { translateErrorMessage } from '../utils/errorMessages'

const BASE = '/api'

export interface User {
  username: string
}

export interface UploadResponse {
  jobId: string
}

export interface JobStatus {
  status: string
  rowsProcessed: number
  rowsFailed: number
  errorSample?: string[]
}

export interface Transaction {
  id: string
  transactionDate: string
  category: string
  description: string
  amount: number
}

export interface TransactionPage {
  items: Transaction[]
  hasMore: boolean
  nextCursor: string | null
}

export interface ByCategoryMonthRow {
  category: string
  month: string
  totalAmount: string | number
}

export interface Summary {
  totalTransactions: number
  totalVolume: number
  distinctCategories: number
  earliestDate: string
  latestDate: string
}

function csrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : null
}

async function request(path: string, options: RequestInit = {}): Promise<unknown> {
  const headers: Record<string, string> = { ...(options.headers as Record<string, string>) }
  const method = ((options.method ?? 'GET') as string).toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') {
    const token = csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let res: Response
  try {
    res = await fetch(`${BASE}${path}`, { ...options, headers, credentials: 'include' })
  } catch {
    throw new Error(translateErrorMessage(null))
  }

  if (!res.ok) {
    let rawMessage: string | null = null
    try {
      const body = (await res.json()) as { message?: string }
      rawMessage = body.message ?? null
    } catch {
      // ignore parse errors
    }
    throw new Error(translateErrorMessage(rawMessage))
  }
  return res.status === 204 ? null : res.json()
}

function requestJson(path: string, method: string, body: unknown): Promise<unknown> {
  return request(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function register(username: string, password: string): Promise<User> {
  return requestJson('/auth/register', 'POST', { username, password }) as Promise<User>
}

export function login(username: string, password: string): Promise<User> {
  return requestJson('/auth/login', 'POST', { username, password }) as Promise<User>
}

export function logout(): Promise<null> {
  return request('/auth/logout', { method: 'POST' }) as Promise<null>
}

export function getCurrentUser(): Promise<User> {
  return request('/auth/me') as Promise<User>
}

export function uploadCsv(file: File): Promise<UploadResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return request('/uploads', { method: 'POST', body: formData }) as Promise<UploadResponse>
}

export function getJobStatus(jobId: string): Promise<JobStatus> {
  return request(`/jobs/${jobId}`) as Promise<JobStatus>
}

interface JobEventHandlers {
  onStatus: (job: JobStatus) => void
  onError?: (e: unknown) => void
}

export function subscribeToJobEvents(jobId: string, { onStatus, onError }: JobEventHandlers): () => void {
  const source = new EventSource(`${BASE}/jobs/${jobId}/events`, { withCredentials: true })

  source.addEventListener('status', (event) => {
    try {
      onStatus(JSON.parse((event as MessageEvent<string>).data) as JobStatus)
    } catch (e) {
      onError?.(e)
    }
  })

  source.onerror = (event) => {
    onError?.(event)
  }

  return () => source.close()
}

interface ListTransactionsOptions {
  cursor?: string | null
  limit?: number
}

export function listTransactions({ cursor, limit = 50 }: ListTransactionsOptions = {}): Promise<TransactionPage> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (cursor) params.set('cursor', cursor)
  return request(`/transactions?${params.toString()}`) as Promise<TransactionPage>
}

interface AggregationOptions {
  from?: string
  to?: string
}

export function getAggregationsByCategoryMonth({ from, to }: AggregationOptions = {}): Promise<ByCategoryMonthRow[]> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return request(`/aggregations/by-category-month${query ? `?${query}` : ''}`) as Promise<ByCategoryMonthRow[]>
}

export function getSummary(): Promise<Summary> {
  return request('/aggregations/summary') as Promise<Summary>
}
