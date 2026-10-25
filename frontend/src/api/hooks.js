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

/** Any change to companies or a sync can change both lists. */
function useRefresh() {
  const client = useQueryClient()
  return () => {
    client.invalidateQueries({ queryKey: ['companies'] })
    client.invalidateQueries({ queryKey: ['jobs'] })
  }
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

export function useSyncAll() {
  const refresh = useRefresh()
  return useMutation({ mutationFn: () => api('/sync', { method: 'POST' }), onSuccess: refresh })
}

export function useSyncCompany() {
  const refresh = useRefresh()
  return useMutation({ mutationFn: (id) => api(`/companies/${id}/sync`, { method: 'POST' }), onSuccess: refresh })
}
