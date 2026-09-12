import { useCallback, useRef, useState } from 'react'

import { helpAssistantMenu, type HelpAssistantMenuItem } from './helpAssistantMenu'

export type HelpAssistantTurn =
  | { id: number; role: 'greeting' }
  | { id: number; role: 'choice'; label: string }
  | { id: number; role: 'reply'; item: HelpAssistantMenuItem }

/**
 * 도우미 대화 상태입니다. 이 단계는 화면만 만들므로 서버·AI를 부르지 않고 고른 메뉴에 정해진 안내를 이어 붙입니다.
 * 나중에 자유 질문을 붙일 때 이 자리에 요청을 넣습니다.
 */
export function useHelpAssistantViewModel() {
  const [turns, setTurns] = useState<HelpAssistantTurn[]>([{ id: 0, role: 'greeting' }])
  const nextId = useRef(0)

  const choose = useCallback((item: HelpAssistantMenuItem) => {
    setTurns((previous) => [
      ...previous,
      { id: (nextId.current += 1), role: 'choice', label: item.label },
      { id: (nextId.current += 1), role: 'reply', item },
    ])
  }, [])

  const restart = useCallback(() => {
    nextId.current = 0
    setTurns([{ id: 0, role: 'greeting' }])
  }, [])

  return { turns, menu: helpAssistantMenu, choose, restart }
}
