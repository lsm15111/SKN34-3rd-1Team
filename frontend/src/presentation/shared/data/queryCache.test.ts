import { describe, expect, it } from 'vitest'

import { QueryCache } from './queryCache'

describe('QueryCache', () => {
  it('이름공간과 키로 저장·조회하고 TTL이 지나면 없는 것으로 본다', () => {
    let now = 1_000
    const cache = new QueryCache({ ttlMs: 500, now: () => now })
    cache.set('catalog', 'a', { total: 1 })
    expect(cache.get('catalog', 'a')).toEqual({ total: 1 })
    expect(cache.get('partners', 'a')).toBeNull()
    now = 1_499
    expect(cache.get('catalog', 'a')).toEqual({ total: 1 })
    now = 1_500
    expect(cache.get('catalog', 'a')).toBeNull()
    expect(cache.size).toBe(0)
  })

  it('용량을 넘기면 가장 오래 안 쓴 항목부터 버리고, 조회한 항목은 최근 사용으로 옮긴다', () => {
    const cache = new QueryCache({ maxEntries: 2 })
    cache.set('c', '1', 1)
    cache.set('c', '2', 2)
    expect(cache.get('c', '1')).toBe(1)
    cache.set('c', '3', 3)
    expect(cache.get('c', '2')).toBeNull()
    expect(cache.get('c', '1')).toBe(1)
    expect(cache.get('c', '3')).toBe(3)
  })

  it('이름공간만 비우거나 전부 비운다', () => {
    const cache = new QueryCache()
    cache.set('catalog', 'a', 1)
    cache.set('catalog', 'b', 2)
    cache.set('partners', 'a', 3)
    cache.invalidate('catalog')
    expect(cache.get('catalog', 'a')).toBeNull()
    expect(cache.get('partners', 'a')).toBe(3)
    cache.invalidate()
    expect(cache.size).toBe(0)
  })

  it('잘못된 용량·TTL은 거부한다', () => {
    expect(() => new QueryCache({ maxEntries: 0 })).toThrow()
    expect(() => new QueryCache({ ttlMs: 0 })).toThrow()
  })
})
