import { useMemo, useState } from 'react'

import { helpEntries } from '../../../shared/help/helpContent'
import type { HelpCategory, HelpEntry } from '../../../shared/help/helpTypes'

/** 도움말 항목의 분류를 화면에서 읽을 수 있는 묶음 이름으로 바꿉니다. */
export const faqGroupTitles: Record<HelpCategory, string> = {
  blocker: '막히기 쉬운 곳',
  concept: '화면에 보이는 말의 뜻',
}

export type FaqGroup = {
  category: HelpCategory
  title: string
  entries: HelpEntry[]
}

function searchable(entry: HelpEntry): string {
  return [entry.question, entry.title, entry.summary, entry.limitation, ...entry.body].join(' ').replace(/\s+/g, '').toLowerCase()
}

/**
 * 자주 묻는 질문 화면의 상태입니다. 질문은 도움말 항목 한 벌에서 그대로 가져오므로 챗봇·매뉴얼과 어긋나지 않습니다.
 * 검색은 화면 안에서만 걸러 네트워크를 쓰지 않습니다.
 */
export function useFaqViewModel() {
  const [keyword, setKeyword] = useState('')

  const matched = useMemo(() => {
    const asked = keyword.replace(/\s+/g, '').toLowerCase()
    if (!asked) return [...helpEntries]
    return helpEntries.filter((entry) => searchable(entry).includes(asked))
  }, [keyword])

  const groups = useMemo<FaqGroup[]>(() => (
    (Object.keys(faqGroupTitles) as HelpCategory[])
      .map((category) => ({
        category,
        title: faqGroupTitles[category],
        entries: matched.filter((entry) => entry.category === category),
      }))
      .filter((group) => group.entries.length > 0)
  ), [matched])

  return { keyword, setKeyword, groups, matchedCount: matched.length, totalCount: helpEntries.length }
}
