import { useEffect, useRef } from 'react'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, toQuery } from './client'

const PAGE_SIZE = 50

export function useJobs(filters) {
  return useInfiniteQuery({
    queryKey: ['jobs', filters],
    queryFn: ({ pageParam }) => api(`/jobs${toQuery({ ...filters, page: pageParam, size: PAGE_SIZE })}`),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  })
}

export function useJob(id) {
  return useQuery({ queryKey: ['job', id], queryFn: () => api(`/jobs/${id}`), enabled: id != null })
}

export function useSummary() {
  return useQuery({ queryKey: ['summary'], queryFn: () => api('/jobs/summary') })
}

export function useCompanies() {
  return useQuery({ queryKey: ['companies'], queryFn: () => api('/companies') })
}

export function useDetect(careersUrl) {
  return useQuery({
    queryKey: ['detect', careersUrl],
    queryFn: () => api('/companies/detect', { method: 'POST', body: { careersUrl } }),
    enabled: careersUrl.trim().length > 8,
    retry: false,
    staleTime: Infinity,
  })
}

/** Anything that changes companies or jobs refreshes every list built from them. */
function useRefresh() {
  const client = useQueryClient()
  return () => ['companies', 'jobs', 'summary', 'job'].forEach((key) => client.invalidateQueries({ queryKey: [key] }))
}

export function useCreateCompany() {
  const refresh = useRefresh()
  return useMutation({ mutationFn: (body) => api('/companies', { method: 'POST', body }), onSuccess: refresh })
}

export function useUpdateCompany() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, ...body }) => api(`/companies/${id}`, { method: 'PATCH', body }),
    onSuccess: refresh,
  })
}

export function useDeleteCompany() {
  const refresh = useRefresh()
  return useMutation({ mutationFn: (id) => api(`/companies/${id}`, { method: 'DELETE' }), onSuccess: refresh })
}

/**
 * The background sync. Polls every 1.5 s while a run is going and once a minute otherwise (the daily run
 * starts on its own). Calls onFinished with the run when one ends, and refreshes the lists.
 */
export function useSyncProgress(onFinished) {
  const refresh = useRefresh()
  const query = useQuery({
    queryKey: ['sync'],
    queryFn: () => api('/sync'),
    refetchInterval: (q) => (q.state.data?.running ? 1500 : 60_000),
  })
  const wasRunning = useRef(false)
  const running = query.data?.running ?? false
  useEffect(() => {
    if (wasRunning.current && !running && query.data) {
      refresh()
      onFinished?.(query.data)
    }
    wasRunning.current = running
    // refresh and onFinished are stable enough; only the running flag should trigger this
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [running])
  return query
}

/** Starts a sync of every company, or of one when given its id. */
export function useStartSync() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (companyId) => api(companyId ? `/companies/${companyId}/sync` : '/sync', { method: 'POST' }),
    onSuccess: (progress) => client.setQueryData(['sync'], progress),
  })
}
