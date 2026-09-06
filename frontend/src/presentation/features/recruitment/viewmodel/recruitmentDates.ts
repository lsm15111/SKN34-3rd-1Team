/** 모집 마감일까지 남은 날수를 화면 문구로 바꿉니다. 브라우저 로컬 날짜 기준입니다. */
export function formatDeadline(closesOn: string, today: Date = new Date()): string {
  const [year, month, day] = closesOn.split('-').map(Number)
  if (!year || !month || !day) return `마감 ${closesOn}`
  const target = new Date(year, month - 1, day)
  const base = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const days = Math.round((target.getTime() - base.getTime()) / 86_400_000)
  if (days < 0) return `마감 ${closesOn}`
  if (days === 0) return '오늘 마감'
  return `모집 마감 D-${days} · ${closesOn}`
}

export function formatDate(isoDateTime: string): string {
  const date = new Date(isoDateTime)
  return Number.isNaN(date.getTime())
    ? isoDateTime
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeZone: 'Asia/Seoul' }).format(date)
}
