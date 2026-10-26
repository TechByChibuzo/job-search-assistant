import { describe, expect, it } from 'vitest'
import { diffWords, plain } from './diff'

describe('diffWords', () => {
  it('marks words added and removed, keeping the rest in order', () => {
    expect(diffWords('Built REST APIs in Java', 'Built Java REST APIs for payments')).toEqual([
      { type: 'same', text: 'Built' },
      { type: 'add', text: 'Java' },
      { type: 'same', text: 'REST APIs' },
      { type: 'del', text: 'in Java' },
      { type: 'add', text: 'for payments' },
    ])
  })

  it('finds nothing to mark when only the bold marks differ', () => {
    expect(diffWords('Managed Kubernetes clusters', plain('Managed **Kubernetes** clusters'))).toEqual([
      { type: 'same', text: 'Managed Kubernetes clusters' },
    ])
  })

  it('handles an empty side', () => {
    expect(diffWords('', 'New line')).toEqual([{ type: 'add', text: 'New line' }])
    expect(diffWords('Old line', '')).toEqual([{ type: 'del', text: 'Old line' }])
  })
})
