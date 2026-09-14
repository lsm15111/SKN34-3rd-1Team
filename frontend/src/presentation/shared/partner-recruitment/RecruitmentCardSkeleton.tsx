/** 모집글 카드 격자의 자리 표시입니다. 첫 진입에 카드가 생길 자리를 미리 차지해 화면이 튀지 않게 합니다. 격자 클래스는 호출 화면의 것을 씁니다. */
export function RecruitmentCardSkeleton({ gridClassName, cards = 3 }: { gridClassName: string; cards?: number }) {
  const bar = 'block rounded-full bg-[#eceff1] motion-safe:animate-pulse'
  return <div className={gridClassName}>
    {Array.from({ length: Math.max(1, cards) }, (_, index) => <div key={index} className="grid min-h-44 gap-3 rounded-[1.25rem] border border-sample-border bg-white p-5">
      <span className={`${bar} h-3 w-1/3`} />
      <span className={`${bar} h-4 w-5/6`} />
      <span className={`${bar} h-3 w-2/3`} />
      <span className={`${bar} mt-auto h-3 w-1/2`} />
    </div>)}
  </div>
}
