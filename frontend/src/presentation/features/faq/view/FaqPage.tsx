import { Link, useLocation } from 'react-router'

import { helpActionHref, helpEntries } from '../../../shared/help/helpContent'
import { isAppPath } from '../../../shared/routes/appPaths'
import { WorkspacePageHeader } from '../../../shared/workspace/WorkspacePageHeader'
import { useFaqViewModel } from '../viewmodel/useFaqViewModel'
import { faqPageStyles as s } from './FaqPage.styles'

/**
 * 검색 엔진과 AI 도우미가 그대로 인용할 수 있게 질문·답변을 구조화해 함께 싣습니다.
 * 답변은 항목의 결론 한 문장만 넣어 화면에 보이는 내용과 어긋나지 않게 합니다.
 */
function FaqStructuredData() {
  const document = {
    '@context': 'https://schema.org',
    '@type': 'FAQPage',
    mainEntity: helpEntries.map((entry) => ({
      '@type': 'Question',
      name: entry.question,
      acceptedAnswer: { '@type': 'Answer', text: entry.summary },
    })),
  }
  return <script type="application/ld+json">{JSON.stringify(document)}</script>
}

/**
 * 자주 묻는 질문 화면입니다. 질문은 도움말 항목 한 벌에서 가져오므로 챗봇이 답하는 내용과 같습니다.
 * 검색·분류·접었다 펴기로 훑어보게 하고, 답마다 갈 곳과 갱신일을 함께 둡니다.
 */
export function FaqPage({ layout = 'public' }: { layout?: 'public' | 'workspace' }) {
  const { pathname } = useLocation()
  const inApp = isAppPath(pathname)
  const { keyword, setKeyword, groups, matchedCount, totalCount } = useFaqViewModel()

  return (
    <>
      {layout === 'workspace' ? <WorkspacePageHeader title="자주 묻는 질문" /> : null}
      <main className={s.page}>
        <FaqStructuredData />

        <section className={s.hero}>
          {layout === 'public' ? <h1 className={s.title}>자주 묻는 질문</h1> : null}
          <p className={s.description}>
            화면 사용법과 표시의 뜻을 모았습니다. 공고 내용은 각 공고의 원문 질문에서 답합니다.
          </p>
        </section>

        <section className={s.group} aria-label="질문 찾기">
          <label className={s.searchLabel} htmlFor="faq-search">질문 검색</label>
          <div className={s.searchBox}>
            <span aria-hidden="true" className={s.searchMark}>⌕</span>
            <input
              className={s.searchInput}
              id="faq-search"
              placeholder="무엇이 궁금한가요?"
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
            />
          </div>
          <p className={s.searchCount} role="status">
            {keyword ? `${matchedCount}건을 찾았습니다.` : `모두 ${totalCount}건입니다.`}
          </p>
        </section>

        {groups.length === 0 ? (
          <p className={s.empty}>찾는 질문이 없습니다. 오른쪽 아래 도움말에서 직접 물어봐 주세요.</p>
        ) : groups.map((group) => (
          <section className={s.group} key={group.category} aria-label={group.title}>
            <h2 className={s.groupTitle}>{group.title}</h2>
            <ul className={s.list}>
              {group.entries.map((entry) => (
                <li className={s.item} key={entry.id}>
                  <details className="group">
                    <summary className={s.question}>
                      {entry.question}
                      <span aria-hidden="true" className={s.questionMark}>⌄</span>
                    </summary>
                    <div className={s.answer}>
                      <p className={s.summary}>{entry.summary}</p>
                      {entry.body.map((paragraph) => <p className={s.paragraph} key={paragraph}>{paragraph}</p>)}
                      <p className={s.limitation}>{entry.limitation}</p>
                      <div className={s.answerFooter}>
                        {entry.action
                          ? <Link className={s.action} to={helpActionHref(entry.action, inApp)}>{entry.action.label}</Link>
                          : <span />}
                        <span className={s.updatedOn}>{entry.updatedOn} 갱신</span>
                      </div>
                    </div>
                  </details>
                </li>
              ))}
            </ul>
          </section>
        ))}

        <section className={s.closing} aria-label="답을 찾지 못했다면">
          <h2 className={s.closingTitle}>찾는 답이 없나요?</h2>
          <p className={s.closingNote}>
            오른쪽 아래 도움말 버튼을 눌러 직접 물어보세요. 같은 내용을 근거로 답하고, 근거가 없으면 답을 만들지 않습니다.
            공고의 지원 대상·금액·마감일은 그 공고의 원문 질문에서 확인해 주세요.
          </p>
        </section>
      </main>
    </>
  )
}
