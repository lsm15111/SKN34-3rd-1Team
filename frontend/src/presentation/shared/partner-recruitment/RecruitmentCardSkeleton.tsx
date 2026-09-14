import { SkeletonCards } from '../loading/Skeleton'

/** 모집글 카드 격자의 자리 표시입니다. 첫 진입에 카드가 생길 자리를 미리 차지해 화면이 튀지 않게 합니다. 격자 클래스는 호출 화면의 것을 씁니다. */
export function RecruitmentCardSkeleton({ gridClassName, cards = 3 }: { gridClassName: string; cards?: number }) {
  return <SkeletonCards gridClassName={gridClassName} cards={cards} />
}
