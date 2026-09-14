/**
 * 작은 영역·대화 상자·폼 안처럼 자리 표시를 그릴 만한 구조가 없는 곳의 짧은 로딩 표시입니다. 회전 아이콘은 보조기기에서 숨기고
 * 문장은 `role="status"`로 읽어 줍니다. 목록·상세처럼 구조가 있는 첫 진입은 `LoadingRegion`을 씁니다.
 */
export function LoadingState({ label, className = '' }: { label: string; className?: string }) {
  return (
    <p role="status" className={`m-0 flex items-center gap-2 text-sm text-sample-muted ${className}`}>
      <span aria-hidden="true" className="block size-4 shrink-0 rounded-full border-2 border-brand-accent border-t-brand-primary motion-safe:animate-spin" />
      <span>{label}</span>
    </p>
  )
}
