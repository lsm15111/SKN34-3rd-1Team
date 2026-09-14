// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { LoadingRegion } from './LoadingRegion'
import { LoadingState } from './LoadingState'
import { SkeletonCards, SkeletonDetail, SkeletonRows, SkeletonText } from './Skeleton'

afterEach(cleanup)

describe('첫 진입 로딩 표시', () => {
  it('LoadingRegion은 스켈레톤을 보조기기에서 숨기고 안내 문장만 status로 읽어 주며 영역을 busy로 표시한다', () => {
    const { container } = render(<LoadingRegion label="모집글을 불러오는 중입니다." skeleton={<SkeletonRows rows={2} />} />)
    const region = container.firstElementChild!
    expect(region.getAttribute('aria-busy')).toBe('true')
    expect(region.querySelector('[aria-hidden="true"]')?.querySelectorAll('span').length).toBe(4)
    expect(screen.getByRole('status').textContent).toBe('모집글을 불러오는 중입니다.')
    expect(screen.getByRole('status').className).toContain('sr-only')
  })

  it('LoadingState는 회전 아이콘을 숨기고 문장만 status로 읽어 준다', () => {
    render(<LoadingState label="관심 공고를 불러오는 중입니다." />)
    const status = screen.getByRole('status')
    expect(status.textContent).toBe('관심 공고를 불러오는 중입니다.')
    expect(status.querySelector('[aria-hidden="true"]')?.className).toContain('motion-safe:animate-spin')
  })

  it('스켈레톤 조각은 요청한 개수만큼 그리고 움직임은 motion-safe에서만 켠다', () => {
    const { container } = render(<>
      <SkeletonText lines={3} />
      <SkeletonDetail />
      <SkeletonCards cards={2} gridClassName="grid" />
    </>)
    const bones = container.querySelectorAll('span')
    expect(bones.length).toBe(3 + 10 + 8)
    bones.forEach((bone) => expect(bone.className).toContain('motion-safe:animate-pulse'))
    expect(container.querySelectorAll('.grid.min-h-44').length).toBe(2)
  })
})
