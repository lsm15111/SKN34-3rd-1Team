import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'

import type { SupportProgram } from '../../../../domain/entities/SupportProgram'
import { catalogSourceCodes, catalogSourceLabels, type SupportProgramCatalogFilters } from '../../../../domain/entities/SupportProgramCatalog'
import { ResultsRegion } from '../../../shared/data/ResultsRegion'
import { isAppPath, supportProgramDetailPath } from '../../../shared/routes/appPaths'
import { defaultCatalogFilters, readCatalogFilters, writeCatalogFilters } from '../../../shared/support-program/catalogSearchParams'
import { FilterChoices } from '../../../shared/workspace/FilterChoices'
import { toFilterChoiceOptions } from '../../../shared/workspace/filterChoiceOptions'
import { useSupportProgramCatalogViewModel } from '../viewmodel/useSupportProgramCatalogViewModel'

const inputStyle = 'min-h-11 w-full min-w-0 rounded-xl border border-sample-border bg-white px-3 text-sm text-app-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary'
const buttonStyle = 'min-h-11 cursor-pointer rounded-xl px-5 text-sm font-bold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-primary disabled:cursor-not-allowed disabled:opacity-40'
const statusLabels = { ALL: '전체 접수 상태', OPEN: '접수 중', UPCOMING: '접수 예정', CLOSED: '접수 마감', UNKNOWN: '상태 미확인' }
const rowGridStyle = 'grid min-w-0 grid-cols-[minmax(0,1fr)_10rem_10rem] gap-5 border-t border-sample-border px-5 py-5 first:border-t-0 max-chat:grid-cols-1 max-chat:gap-2 max-chat:px-4'

/**
 * 자연어 추천과 구분되는 DB 목록 화면입니다. AI 자격 판정을 표시하지 않습니다.
 * 조건이 바뀌는 동안 이전 목록을 유지하고(흐리게), 첫 진입은 스켈레톤을, 뒤로 가기는 캐시를 먼저 보여 줍니다. 필터는 조회 중에도 잠그지 않습니다.
 */
export function SupportProgramCatalogPanel() {
  const [params, setParams] = useSearchParams()
  const filters = readCatalogFilters(params)
  const { pathname } = useLocation()
  const catalog = useSupportProgramCatalogViewModel(filters)
  const resultsRef = useRef<HTMLElement>(null)
  // 사용자가 조건을 바꿔 새 결과가 왔을 때만 결과 상단으로 옮깁니다. 뒤로 가기 복원은 브라우저에 맡깁니다.
  const scrollOnReadyRef = useRef(false)
  const apply = (next: SupportProgramCatalogFilters) => {
    scrollOnReadyRef.current = true
    setParams(writeCatalogFilters(next))
  }
  useEffect(() => {
    if (catalog.phase === 'loading' || !scrollOnReadyRef.current) return
    // 실패했을 때도 표시를 지워야 다음 재시도·뒤로 가기 응답에 엉뚱하게 스크롤하지 않습니다.
    scrollOnReadyRef.current = false
    if (catalog.phase !== 'ready') return
    const results = resultsRef.current
    if (results && typeof results.scrollIntoView === 'function') {
      results.scrollIntoView({ block: 'start', behavior: window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    }
  }, [catalog.phase, catalog.data])
  const returnTo = `${pathname}?${writeCatalogFilters(filters)}`
  const pageStart = Math.max(1, Math.min(filters.page - 2, (catalog.data?.totalPages ?? 1) - 4))
  const errorCard = <div role="alert" className="rounded-2xl border border-sample-border p-8 text-center">
    <p className="text-sm text-sample-muted">공고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
    <button type="button" className={`${buttonStyle} bg-brand-primary text-white`} onClick={catalog.retry}>다시 불러오기</button>
  </div>
  return (
    <main className="flex-1 bg-white text-app-ink">
      <div className="mx-auto grid w-full max-w-6xl gap-6 px-6 pt-6 pb-12 max-chat:gap-5 max-chat:px-4 max-chat:pt-4">
        <header>
          <p className="m-0 text-xs font-bold text-brand-primary">필터로 빠르게 찾기</p>
          <h1 className="mt-2 mb-2 text-3xl font-bold tracking-tight max-chat:text-2xl">원하는 지원사업을 직접 골라보세요.</h1>
          <p className="m-0 text-sm leading-relaxed text-sample-muted">분야와 지역을 선택하면 저장된 공고를 바로 볼 수 있어요.</p>
        </header>
        <CatalogFilters key={JSON.stringify(filters)} filters={filters} regions={catalog.regions} categories={catalog.categories}
          startupStages={catalog.startupStages} applicantTypes={catalog.applicantTypes} founderAges={catalog.founderAges}
          isSearching={catalog.isRefreshing} onApply={apply} />
        <AppliedCatalogFilters filters={filters} onApply={apply} />
        {/* 고정 헤더(비로그인 약 94px, 작업 화면 약 70px) 아래로 결과 제목이 숨지 않도록 스크롤 여백을 둡니다. */}
        <section ref={resultsRef} aria-label="필터 검색 결과" className="min-w-0 scroll-mt-28">
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <h2 className="m-0 text-base font-bold" aria-live="polite">{catalog.data ? <>검색 결과 <span className="text-brand-primary">{catalog.data.total.toLocaleString()}건</span></> : '검색 결과'}</h2>
            <label className="flex items-center gap-2 text-xs text-sample-muted">정렬
              <select aria-label="공고 정렬" className={`${inputStyle} !min-h-9 !w-auto !text-xs`} value={filters.sort}
                onChange={(event) => apply({ ...filters, sort: event.target.value as SupportProgramCatalogFilters['sort'], page: 1 })}>
                <option value="RECENT">최신순</option><option value="DEADLINE">마감일순</option>
              </select>
            </label>
          </div>
          <ResultsRegion phase={catalog.phase} hasData={catalog.data !== null} subtle={catalog.isStale} loadingLabel="공고를 불러오고 있어요…"
            refreshLabel="공고 결과를 갱신하는 중입니다" failedLabel="새 조건의 공고를 불러오지 못했습니다. 이전 결과를 보여 드리고 있어요."
            skeleton={<CatalogRowSkeleton rows={filters.pageSize} />} error={errorCard} onRetry={catalog.retry}>
            {catalog.data?.programs.length ? (
              <div className="overflow-hidden rounded-2xl border border-sample-border">
                <div aria-hidden="true" className="grid grid-cols-[minmax(0,1fr)_10rem_10rem] gap-5 bg-[#f7f8f9] px-5 py-3 text-xs font-semibold text-sample-muted max-chat:hidden">
                  <span>지원사업명 · 분야</span><span>기관</span><span>접수 상태 · 신청 기간</span>
                </div>
                {catalog.data.programs.map((program) => <CatalogRow key={JSON.stringify([program.sourceCode, program.id])} program={program} returnTo={returnTo} inApp={isAppPath(pathname)} />)}
              </div>
            ) : <div className="rounded-2xl border border-sample-border p-10 text-center">
              <h3 className="m-0 text-base font-bold">{catalog.data?.total ? '이 페이지에는 공고가 없어요.' : '조건에 맞는 공고가 없어요.'}</h3>
              <p className="text-sm text-sample-muted">{catalog.data?.total ? '공고 목록이 바뀌었을 수 있어요. 첫 페이지를 확인해 주세요.' : '검색어를 줄이거나 지역·분야·접수 상태를 넓혀보세요.'}</p>
              <button type="button" className={`${buttonStyle} border border-sample-border bg-white`} onClick={() => apply(catalog.data?.total ? { ...filters, page: 1 } : { ...defaultCatalogFilters, status: 'ALL' })}>
                {catalog.data?.total ? '첫 페이지로' : '전체 공고 보기'}
              </button>
            </div>}
            {catalog.data && catalog.data.totalPages > 1 ? <nav aria-label="공고 페이지" className="mt-6 flex flex-wrap justify-center gap-1.5">
              <button type="button" className={`${buttonStyle} !px-3 text-sample-muted`} disabled={filters.page <= 1} onClick={() => apply({ ...filters, page: filters.page - 1 })}>이전</button>
              {Array.from({ length: Math.min(5, catalog.data.totalPages) }, (_, index) => pageStart + index).map((page) => (
                <button type="button" key={page} aria-label={`${page}페이지`} aria-current={page === filters.page ? 'page' : undefined}
                  className={`${buttonStyle} !px-3.5 ${page === filters.page ? 'bg-brand-primary text-white' : 'text-sample-muted hover:bg-[#f5f6f7]'}`}
                  onClick={() => apply({ ...filters, page })}>{page}</button>
              ))}
              <button type="button" className={`${buttonStyle} !px-3 text-sample-muted`} disabled={filters.page >= catalog.data.totalPages} onClick={() => apply({ ...filters, page: filters.page + 1 })}>다음</button>
            </nav> : null}
          </ResultsRegion>
        </section>
        <p className="m-0 text-xs leading-relaxed text-sample-muted">필터는 제공처의 공고 분류이며 신청 자격 판정이 아닙니다. 전국 공고는 ‘전국’을 선택해 확인하세요. 신청 자격은 공고 원문에서 확인해 주세요.</p>
      </div>
    </main>
  )
}

/** 실제 행과 같은 그리드·높이의 자리 표시입니다. 첫 진입에 목록이 생길 자리를 미리 차지해 화면이 튀지 않게 합니다. */
function CatalogRowSkeleton({ rows }: { rows: number }) {
  const bar = 'block h-3 rounded-full bg-[#eceff1] motion-safe:animate-pulse'
  return <div className="overflow-hidden rounded-2xl border border-sample-border">
    {Array.from({ length: Math.max(1, rows) }, (_, index) => <div key={index} className={rowGridStyle}>
      <div className="min-w-0"><span className={`${bar} mb-3 w-1/4`} /><span className={`${bar} h-4 w-3/4`} /></div>
      <span className={`${bar} w-24 self-center`} />
      <div className="self-center"><span className={`${bar} mb-2 h-5 w-14 rounded-full`} /><span className={`${bar} w-28`} /></div>
    </div>)}
  </div>
}

/** 적용된 조건을 폼(초안)과 구분해 보여 주고, ×로 그 조건만 빼고 바로 다시 검색합니다. 기본값(접수 중·전체)은 표시하지 않습니다. */
function AppliedCatalogFilters({ filters, onApply }: { filters: SupportProgramCatalogFilters; onApply: (filters: SupportProgramCatalogFilters) => void }) {
  const chips: { label: string; clear: () => SupportProgramCatalogFilters }[] = []
  if (filters.keyword) chips.push({ label: `검색어: ${filters.keyword}`, clear: () => ({ ...filters, keyword: '' }) })
  if (filters.region) chips.push({ label: `지역: ${filters.region}`, clear: () => ({ ...filters, region: '' }) })
  if (filters.category) chips.push({ label: `분야: ${filters.category}`, clear: () => ({ ...filters, category: '' }) })
  if (filters.sourceCode) chips.push({ label: `출처: ${catalogSourceLabels[filters.sourceCode]}`, clear: () => ({ ...filters, sourceCode: '', startupStage: '', applicantType: '', founderAge: '' }) })
  if (filters.status !== defaultCatalogFilters.status) chips.push({ label: `접수 상태: ${statusLabels[filters.status]}`, clear: () => ({ ...filters, status: defaultCatalogFilters.status }) })
  if (filters.startupStage) chips.push({ label: `창업 업력: ${filters.startupStage}`, clear: () => ({ ...filters, startupStage: '' }) })
  if (filters.applicantType) chips.push({ label: `신청 대상: ${filters.applicantType}`, clear: () => ({ ...filters, applicantType: '' }) })
  if (filters.founderAge) chips.push({ label: `대표자 연령: ${filters.founderAge}`, clear: () => ({ ...filters, founderAge: '' }) })
  if (chips.length === 0) return null
  return <div className="flex flex-wrap items-center gap-2" role="group" aria-label="적용된 필터">
    {chips.map((chip) => <span key={chip.label} className="inline-flex min-h-9 items-center gap-1 rounded-full border border-[#b4ddc7] bg-brand-accent pl-3 pr-1 text-xs font-bold text-brand-primary">
      {chip.label}
      <button type="button" aria-label={`${chip.label} 해제`} className="grid size-7 cursor-pointer place-items-center rounded-full text-base leading-none hover:bg-white/70 focus-visible:outline-2 focus-visible:outline-brand-primary"
        onClick={() => onApply({ ...chip.clear(), page: 1 })}>×</button>
    </span>)}
    <button type="button" className="cursor-pointer rounded px-1 py-1 text-xs font-semibold text-sample-muted hover:text-brand-primary focus-visible:outline-2 focus-visible:outline-brand-primary"
      onClick={() => onApply({ ...defaultCatalogFilters })}>모두 해제</button>
  </div>
}

function CatalogFilters({ filters, regions, categories, startupStages, applicantTypes, founderAges, isSearching, onApply }: {
  filters: SupportProgramCatalogFilters; regions: string[]; categories: string[]
  startupStages: string[]; applicantTypes: string[]; founderAges: string[]
  /** 이전 결과를 보여 주며 새 조건을 조회하는 중입니다. 버튼 이름은 그대로 두고 표시만 바꿉니다. */
  isSearching: boolean
  onApply: (filters: SupportProgramCatalogFilters) => void
}) {
  const [draft, setDraft] = useState(filters)
  const [showStartupFilters, setShowStartupFilters] = useState(Boolean(filters.startupStage || filters.applicantType || filters.founderAge))
  const startupFilterCount = [draft.startupStage, draft.applicantType, draft.founderAge].filter(Boolean).length
  const needsPeriodNotice = draft.sourceCode === 'MSIT' || draft.sourceCode === 'CNTRADE_NOTICE'
  return <form aria-label="공고 필터" className="grid gap-4 rounded-3xl border border-sample-border bg-white p-5 shadow-[0_4px_24px_rgb(32_33_36_/_3%)] max-chat:p-4"
    onSubmit={(event) => { event.preventDefault(); onApply({ ...draft, keyword: draft.keyword.trim(), page: 1 }) }}>
    <div className="flex items-end gap-2">
      <label className="grid min-w-0 flex-1 gap-2 text-xs font-semibold text-sample-muted">검색어
        <input type="search" className={inputStyle} aria-label="공고명 또는 기관명" placeholder="공고명이나 기관명을 입력하세요" maxLength={100}
          value={draft.keyword} onChange={(event) => setDraft({ ...draft, keyword: event.target.value })} />
      </label>
      <button type="submit" aria-busy={isSearching} className={`${buttonStyle} inline-flex shrink-0 items-center gap-2 bg-brand-primary text-white hover:bg-[#066538]`}>
        검색{isSearching ? <span aria-hidden="true" className="block size-3.5 rounded-full border-2 border-white/40 border-t-white motion-safe:animate-spin" /> : null}
      </button>
    </div>
    <div className="grid gap-4 border-t border-sample-border pt-4">
      <FilterChoices label="지역" name="catalog-region" options={toFilterChoiceOptions(regions)} selected={draft.region} onSelect={(region) => setDraft({ ...draft, region })} />
      <FilterChoices label="분야" name="catalog-category" options={toFilterChoiceOptions(categories)} selected={draft.category} onSelect={(category) => setDraft({ ...draft, category })} />
      <div className="grid gap-3 sm:grid-cols-2 sm:gap-x-8">
        <label className="flex min-w-0 items-center gap-3 text-xs font-semibold text-sample-muted">출처
          <select className={`${inputStyle} !min-h-9 !w-auto flex-1 sm:max-w-64`} value={draft.sourceCode}
            onChange={(event) => {
              const sourceCode = event.target.value as SupportProgramCatalogFilters['sourceCode']
              setDraft({ ...draft, sourceCode, startupStage: '', applicantType: '', founderAge: '' })
              setShowStartupFilters(false)
            }}>
            {catalogSourceCodes.map((code) => <option key={code} value={code}>{catalogSourceLabels[code]}</option>)}
          </select>
        </label>
        <label className="flex min-w-0 items-center gap-3 text-xs font-semibold text-sample-muted">접수 상태
          <select className={`${inputStyle} !min-h-9 !w-auto flex-1 sm:max-w-52`} value={draft.status}
            aria-describedby={needsPeriodNotice ? 'catalog-period-notice' : undefined}
            onChange={(event) => setDraft({ ...draft, status: event.target.value as SupportProgramCatalogFilters['status'] })}>
            {Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
      </div>
      {needsPeriodNotice ? <p id="catalog-period-notice" className="m-0 rounded-xl bg-[#f7f8f9] px-3 py-2 text-xs leading-relaxed text-sample-muted">
        접수 기간을 제공하지 않는 공고는 ‘상태 미확인’에 표시됩니다. ‘전체 접수 상태’ 또는 ‘상태 미확인’으로 검색해 주세요.
      </p> : null}
      {draft.sourceCode === 'KSTARTUP' ? <div className="min-w-0 rounded-xl bg-[#f7f8f9] px-3 py-2">
        <button type="button" aria-expanded={showStartupFilters} aria-controls="catalog-startup-filters"
          className="flex min-h-9 w-full cursor-pointer items-center justify-between gap-2 rounded text-left text-xs font-semibold text-sample-muted focus-visible:outline-2 focus-visible:outline-brand-primary"
          onClick={() => setShowStartupFilters((value) => !value)}>
          <span>K-Startup 추가 조건{startupFilterCount ? ` · ${startupFilterCount}개 선택` : ''}</span>
          <span aria-hidden="true">{showStartupFilters ? '−' : '+'}</span>
        </button>
        <div id="catalog-startup-filters" hidden={!showStartupFilters}>
          <div className="grid min-w-0 gap-3 pt-2 pb-3 sm:grid-cols-3">
            <CatalogExtraSelect label="창업 업력" options={startupStages} selected={draft.startupStage} onSelect={(startupStage) => setDraft({ ...draft, startupStage })} />
            <CatalogExtraSelect label="신청 대상" options={applicantTypes} selected={draft.applicantType} onSelect={(applicantType) => setDraft({ ...draft, applicantType })} />
            <CatalogExtraSelect label="대표자 연령" options={founderAges} selected={draft.founderAge} onSelect={(founderAge) => setDraft({ ...draft, founderAge })} />
          </div>
          <p className="mt-0 mb-2 text-xs leading-relaxed text-sample-muted">공고 분류 기준입니다. 실제 신청 자격은 원문을 확인해 주세요.</p>
        </div>
      </div> : null}
    </div>
    <div className="flex flex-wrap items-center justify-between gap-2 border-t border-sample-border pt-3">
      <p className="m-0 text-xs text-sample-muted">필터를 바꾼 뒤 검색을 눌러주세요.</p>
      <button type="button" className="cursor-pointer rounded px-1 py-1 text-xs font-semibold text-brand-primary focus-visible:outline-2 focus-visible:outline-brand-primary"
        onClick={() => { setDraft({ ...defaultCatalogFilters }); setShowStartupFilters(false); onApply({ ...defaultCatalogFilters }) }}>필터 초기화</button>
    </div>
  </form>
}

function CatalogExtraSelect({ label, options, selected, onSelect }: {
  label: string; options: string[]; selected: string; onSelect: (value: string) => void
}) {
  const choices = selected && !options.includes(selected) ? [selected, ...options] : options
  return <label className="grid min-w-0 gap-2 text-xs font-semibold text-sample-muted">{label}
    <select className={inputStyle} value={selected} onChange={(event) => onSelect(event.target.value)}>
      <option value="">전체</option>
      {choices.map((value) => <option key={value} value={value}>{value}</option>)}
    </select>
  </label>
}
function CatalogRow({ program, returnTo, inApp }: { program: SupportProgram; returnTo: string; inApp: boolean }) {
  const detailPath = supportProgramDetailPath({ sourceCode: program.sourceCode, sourceProgramId: program.id }, inApp)
  return <article className="grid min-w-0 grid-cols-[minmax(0,1fr)_10rem_10rem] gap-5 border-t border-sample-border px-5 py-5 first:border-t-0 hover:bg-[#fafcfb] max-chat:grid-cols-1 max-chat:gap-2 max-chat:px-4">
    <div className="min-w-0">
      <p className="mt-0 mb-2 truncate text-xs font-semibold text-brand-primary">{program.categories.join(' · ') || '분야 미분류'} <span className="font-normal text-sample-muted">{program.regions.length ? ` / ${program.regions.join(' · ')}` : ''}</span></p>
      <h3 className="m-0 text-sm font-bold leading-relaxed [overflow-wrap:anywhere]"><Link className="rounded hover:text-brand-primary focus-visible:outline-2 focus-visible:outline-brand-primary"
        to={detailPath} state={{ searchReturnTo: returnTo }}>{program.title}</Link></h3>
    </div>
    <p className="m-0 self-center text-xs leading-relaxed text-sample-muted [overflow-wrap:anywhere]">{program.organization || program.sourceName}</p>
    <div className="self-center max-chat:flex max-chat:flex-wrap max-chat:items-center max-chat:gap-2">
      <span className={`inline-block rounded-full px-2 py-1 text-[0.68rem] font-bold ${program.status === 'OPEN' ? 'bg-brand-accent text-brand-primary' : 'bg-[#f1f3f4] text-sample-muted'}`}>{statusLabels[program.status]}</span>
      <p className="mt-1 mb-0 text-xs leading-relaxed text-sample-muted [overflow-wrap:anywhere]">{program.applicationPeriod}</p>
    </div>
  </article>
}
