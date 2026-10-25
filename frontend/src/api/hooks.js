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
  return () => ['companies', 'jobs', 'summary', 'job', 'gmail'].forEach((key) => client.invalidateQueries({ queryKey: [key] }))
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

// Tracker

/** Everything the tracker changes shows up in several places, so each change refreshes them all. */
function useRefreshTracker() {
  const client = useQueryClient()
  return () =>
    ['pursuits', 'tracking', 'due', 'contacts', 'outreach', 'jobs', 'job', 'companies', 'summary'].forEach((key) =>
      client.invalidateQueries({ queryKey: [key] }),
    )
}

export function usePursuits() {
  return useQuery({ queryKey: ['pursuits'], queryFn: () => api('/pursuits') })
}

export function useTracking(jobId) {
  return useQuery({ queryKey: ['tracking', jobId], queryFn: () => api(`/jobs/${jobId}/tracking`), enabled: jobId != null })
}

export function useDue() {
  return useQuery({ queryKey: ['due'], queryFn: () => api('/outreach/due'), refetchInterval: 300_000 })
}

export function useContacts(companyId) {
  return useQuery({
    queryKey: ['contacts', companyId ?? 'all'],
    queryFn: () => api(`/contacts${toQuery({ companyId })}`),
  })
}

export function useContactOutreach(contactId) {
  return useQuery({
    queryKey: ['outreach', 'contact', contactId],
    queryFn: () => api(`/contacts/${contactId}/outreach`),
    enabled: contactId != null,
  })
}

function useTrackerMutation(fn) {
  const refresh = useRefreshTracker()
  return useMutation({ mutationFn: fn, onSuccess: refresh })
}

export const useTrack = () => useTrackerMutation((jobId) => api('/pursuits', { method: 'POST', body: { jobId } }))
export const useUpdatePursuit = () =>
  useTrackerMutation(({ id, ...body }) => api(`/pursuits/${id}`, { method: 'PATCH', body }))
export const useUntrack = () => useTrackerMutation((id) => api(`/pursuits/${id}`, { method: 'DELETE' }))
export const useLogOutreach = () => useTrackerMutation((body) => api('/outreach', { method: 'POST', body }))
export const useRecordEvent = () =>
  useTrackerMutation(({ id, ...body }) => api(`/outreach/${id}/events`, { method: 'POST', body }))
export const useDeleteOutreach = () => useTrackerMutation((id) => api(`/outreach/${id}`, { method: 'DELETE' }))
export const useCreateContact = () => useTrackerMutation((body) => api('/contacts', { method: 'POST', body }))
export const useUpdateContact = () =>
  useTrackerMutation(({ id, ...body }) => api(`/contacts/${id}`, { method: 'PATCH', body }))
export const useDeleteContact = () => useTrackerMutation((id) => api(`/contacts/${id}`, { method: 'DELETE' }))
export const useAddJob = () => useTrackerMutation((body) => api('/jobs', { method: 'POST', body }))
export const useSetJobClosed = () =>
  useTrackerMutation(({ id, closed }) => api(`/jobs/${id}`, { method: 'PATCH', body: { closed } }))

// Job-alert emails

export function useGmail() {
  return useQuery({ queryKey: ['gmail'], queryFn: () => api('/gmail') })
}

export function useGmailImport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api('/gmail/import', { method: 'POST' }),
    onSuccess: (progress) => client.setQueryData(['sync'], progress),
  })
}

export function useGmailDisconnect() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => api('/gmail', { method: 'DELETE' }),
    onSuccess: () => client.invalidateQueries({ queryKey: ['gmail'] }),
  })
}

// Claude: match scores and drafts

export function useAi() {
  return useQuery({ queryKey: ['ai'], queryFn: () => api('/ai') })
}

export function useMatch() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (jobId) => api(`/jobs/${jobId}/match`, { method: 'POST' }),
    onSuccess: (_, jobId) => {
      client.invalidateQueries({ queryKey: ['job', jobId] })
      client.invalidateQueries({ queryKey: ['jobs'] })
    },
  })
}

export function useDraft() {
  return useMutation({ mutationFn: (body) => api('/drafts', { method: 'POST', body }) })
}

// Applicant profile, for the extension's form filling

export function useProfile() {
  return useQuery({ queryKey: ['profile'], queryFn: () => api('/profile') })
}

export function useSaveProfile() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (fields) => api('/profile', { method: 'PUT', body: fields }),
    onSuccess: (profile) => client.setQueryData(['profile'], profile),
  })
}

export function useProfileFromResume() {
  return useMutation({ mutationFn: () => api('/profile/from-resume', { method: 'POST' }) })
}
