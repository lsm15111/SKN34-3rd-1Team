import type { KeyboardEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'

import { recruitmentRoleLabels } from '../../../../domain/entities/RecruitmentPost'
import { RequireSignIn } from '../../auth/view/RequireSignIn'
import {
  type ProgramSummary,
  type RecruitmentPostFormMode,
  useRecruitmentPostFormViewModel,
} from '../viewmodel/useRecruitmentPostFormViewModel'
import { recruitmentStyles } from './Recruitment.styles'

const programStatusLabels = { OPEN: '접수 중', UPCOMING: '접수 예정', CLOSED: '접수 종료', UNKNOWN: '기간 확인 필요' } as const

/** 모집글 작성(/partners/new)과 수정(/partners/:postId/edit)을 하나의 폼으로 다룹니다. 로그인이 필요합니다. */
export function RecruitmentPostFormPage() {
  const params = useParams()
  const [searchParams] = useSearchParams()
  const editingId = params.postId ? Number.parseInt(params.postId, 10) : null
  const sourceCode = searchParams.get('sourceCode')?.trim()
  const sourceProgramId = searchParams.get('sourceProgramId')?.trim()
  const mode: RecruitmentPostFormMode = editingId && Number.isInteger(editingId) && editingId > 0
    ? { kind: 'edit', postId: editingId }
    : { kind: 'create', initialProgram: sourceCode && sourceProgramId ? { sourceCode, sourceProgramId } : null }

  return (
    <RequireSignIn>
      <RecruitmentPostForm key={mode.kind === 'edit' ? `edit-${mode.postId}` : 'create'} mode={mode} />
    </RequireSignIn>
  )
}

function RecruitmentPostForm({ mode }: { mode: RecruitmentPostFormMode }) {
  const viewModel = useRecruitmentPostFormViewModel(mode)
  const { errors, isSubmitting, loadState, registerField, submit, submitError } = viewModel

  if (loadState === 'loading') {
    return <main className={recruitmentStyles.narrowPage}><p className={recruitmentStyles.hint}>모집글을 불러오는 중입니다.</p></main>
  }
  if (loadState !== 'ready') {
    const message = {
      'not-found': '모집글을 찾을 수 없습니다.',
      forbidden: '작성 기업만 이 모집글을 수정할 수 있습니다.',
      'not-open': '이미 종료된 모집글은 수정할 수 없습니다.',
      failed: '모집글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    }[loadState]
    return (
      <main className={recruitmentStyles.narrowPage}>
        <Link className={recruitmentStyles.backLink} to="/partners">← 파트너 모집 목록</Link>
        <h1 className={recruitmentStyles.title}>모집글을 수정할 수 없습니다</h1>
        <p className={recruitmentStyles.description}>{message}</p>
      </main>
    )
  }

  function handleProgramSearchKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
    event.preventDefault()
    void viewModel.searchPrograms()
  }

  return (
    <main className={recruitmentStyles.narrowPage}>
      <header className={recruitmentStyles.header}>
        <div>
          <Link className={recruitmentStyles.backLink} to={mode.kind === 'edit' ? `/partners/${mode.postId}` : '/partners'}>
            ← {mode.kind === 'edit' ? '모집글 상세' : '파트너 모집 목록'}
          </Link>
          <p className={recruitmentStyles.eyebrow}>파트너 모집</p>
          <h1 className={recruitmentStyles.title}>{mode.kind === 'edit' ? '모집글 수정' : '모집글 작성'}</h1>
          <p className={recruitmentStyles.description}>
            연락처·이메일·금액 확약은 본문에 적지 마세요. 담당자 정보는 제안 수락 뒤 자동으로 공개됩니다.
          </p>
        </div>
      </header>

      <form className={recruitmentStyles.form} noValidate onSubmit={submit}>
        <fieldset className={recruitmentStyles.fieldset}>
          <legend className={recruitmentStyles.legend}>1. 연결할 공고</legend>
          {viewModel.selectedProgram ? (
            <SelectedProgram
              program={viewModel.selectedProgram}
              onClear={viewModel.canChangeProgram ? viewModel.clearProgram : undefined}
            />
          ) : (
            <p className={recruitmentStyles.fieldHint}>
              {viewModel.canChangeProgram
                ? '모집글은 공식 공고 하나에 반드시 묶입니다. 공고명으로 검색해 선택하세요.'
                : '연결된 공고가 더 이상 공개되지 않습니다.'}
            </p>
          )}
          {viewModel.canChangeProgram && !viewModel.selectedProgram ? (
            <div className="grid gap-3">
              <div className={recruitmentStyles.searchRow} role="search">
                <input
                  className={recruitmentStyles.input}
                  type="search"
                  aria-label="공고 검색"
                  placeholder="예: 서울 AI 실증 지원사업"
                  value={viewModel.programQuery}
                  onChange={(event) => viewModel.setProgramQuery(event.target.value)}
                  onKeyDown={handleProgramSearchKey}
                />
                <button
                  type="button"
                  className={recruitmentStyles.secondaryButton}
                  onClick={() => void viewModel.searchPrograms()}
                  disabled={viewModel.programSearch.status === 'searching'}
                >
                  {viewModel.programSearch.status === 'searching' ? '검색 중…' : '공고 검색'}
                </button>
              </div>
              {viewModel.programSearch.status === 'failed'
                ? <p className={recruitmentStyles.fieldError} role="alert">공고를 검색하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
                : null}
              {viewModel.programSearch.status === 'results' ? (
                viewModel.programSearch.programs.length === 0
                  ? <p className={recruitmentStyles.fieldHint}>현재 접수 중인 공고 중 일치하는 결과가 없습니다.</p>
                  : (
                    <ul className={`${recruitmentStyles.searchResults} m-0 list-none p-0`} aria-label="공고 검색 결과">
                      {viewModel.programSearch.programs.map((program) => (
                        <li key={`${program.sourceCode}:${program.sourceProgramId}`}>
                          <button type="button" className={`${recruitmentStyles.searchResult} w-full`} onClick={() => viewModel.selectProgram(program)}>
                            <span className={recruitmentStyles.searchResultTitle}>{program.title}</span>
                            <span className={recruitmentStyles.searchResultMeta}>
                              {program.organization} · {programStatusLabels[program.status]}
                              {program.applicationEndDate ? ` · 공고 마감 ${program.applicationEndDate}` : ''}
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )
              ) : null}
            </div>
          ) : null}
        </fieldset>

        <fieldset className={recruitmentStyles.fieldset}>
          <legend className={recruitmentStyles.legend}>2. 역할과 조건</legend>
          <div className={recruitmentStyles.twoColumns}>
            <div className={recruitmentStyles.field}>
              우리 기업의 역할
              <div className={recruitmentStyles.radioRow} role="radiogroup" aria-label="우리 기업의 역할">
                {(['LEAD', 'PARTICIPANT'] as const).map((role) => (
                  <label key={role} className={recruitmentStyles.radioOption}>
                    <input type="radio" value={role} {...registerField('ourRole')} />
                    {recruitmentRoleLabels[role]}
                  </label>
                ))}
              </div>
              {errors.ourRole?.message ? <span className={recruitmentStyles.fieldError}>{errors.ourRole.message}</span> : null}
            </div>
            <div className={recruitmentStyles.field}>
              찾는 역할
              <div className={recruitmentStyles.radioRow} role="radiogroup" aria-label="찾는 역할">
                {(['LEAD', 'PARTICIPANT', 'DEMAND'] as const).map((role) => (
                  <label key={role} className={recruitmentStyles.radioOption}>
                    <input type="radio" value={role} {...registerField('wantedRole')} />
                    {recruitmentRoleLabels[role]}
                  </label>
                ))}
              </div>
              {errors.wantedRole?.message ? <span className={recruitmentStyles.fieldError}>{errors.wantedRole.message}</span> : null}
            </div>
          </div>
          <div className={recruitmentStyles.twoColumns}>
            <label className={recruitmentStyles.field} htmlFor="wanted-company-count">
              찾는 기업 수
              <input id="wanted-company-count" className={recruitmentStyles.input} type="number" min={1} max={10} aria-invalid={errors.wantedCompanyCount ? true : undefined} {...registerField('wantedCompanyCount', { valueAsNumber: true })} />
              {errors.wantedCompanyCount?.message ? <span className={recruitmentStyles.fieldError}>{errors.wantedCompanyCount.message}</span> : null}
            </label>
            <label className={recruitmentStyles.field} htmlFor="closes-on">
              모집 마감일
              <input id="closes-on" className={recruitmentStyles.input} type="date" aria-invalid={errors.closesOn ? true : undefined} {...registerField('closesOn')} />
              <span className={recruitmentStyles.fieldHint}>
                {viewModel.selectedProgram?.applicationEndDate
                  ? `공고 마감 ${viewModel.selectedProgram.applicationEndDate} 이전이어야 하며, 공고가 먼저 마감되면 모집도 종료됩니다.`
                  : '공고가 먼저 마감되면 모집도 자동 종료됩니다.'}
              </span>
              {errors.closesOn?.message ? <span className={recruitmentStyles.fieldError}>{errors.closesOn.message}</span> : null}
            </label>
          </div>
          <label className={recruitmentStyles.field} htmlFor="wanted-region">
            희망 지역
            <input id="wanted-region" className={recruitmentStyles.input} placeholder="예: 서울·경기 (비우면 무관)" aria-invalid={errors.wantedRegion ? true : undefined} {...registerField('wantedRegion')} />
            {viewModel.selectedProgram?.targetDescription
              ? <span className={recruitmentStyles.fieldHint}>공고 지원대상 원문: {viewModel.selectedProgram.targetDescription}</span>
              : null}
            {errors.wantedRegion?.message ? <span className={recruitmentStyles.fieldError}>{errors.wantedRegion.message}</span> : null}
          </label>
          <label className={recruitmentStyles.field} htmlFor="required-capabilities">
            필요 역량
            <input id="required-capabilities" className={recruitmentStyles.input} placeholder="쉼표로 구분 · 예: 데이터 구축, 라벨링 운영" aria-invalid={errors.requiredCapabilitiesText ? true : undefined} {...registerField('requiredCapabilitiesText')} />
            <span className={recruitmentStyles.fieldHint}>최대 10개, 각 30자 이하.</span>
            {errors.requiredCapabilitiesText?.message ? <span className={recruitmentStyles.fieldError}>{errors.requiredCapabilitiesText.message}</span> : null}
          </label>
        </fieldset>

        <fieldset className={recruitmentStyles.fieldset}>
          <legend className={recruitmentStyles.legend}>3. 소개</legend>
          <label className={recruitmentStyles.field} htmlFor="post-title">
            제목
            <input id="post-title" className={recruitmentStyles.input} maxLength={80} aria-invalid={errors.title ? true : undefined} {...registerField('title')} />
            {errors.title?.message ? <span className={recruitmentStyles.fieldError}>{errors.title.message}</span> : null}
          </label>
          <label className={recruitmentStyles.field} htmlFor="post-body">
            모집 소개
            <textarea id="post-body" className={recruitmentStyles.textarea} maxLength={2000} aria-invalid={errors.body ? true : undefined} {...registerField('body')} />
            <span className={recruitmentStyles.fieldHint}>
              우리가 맡을 일과 상대에게 바라는 일을 나눠 적고, 일정과 예산 비율은 협의 범위로 적으세요. 2,000자 이하.
            </span>
            {errors.body?.message ? <span className={recruitmentStyles.fieldError}>{errors.body.message}</span> : null}
          </label>
        </fieldset>

        {submitError ? <p className={recruitmentStyles.error} role="alert">{submitError}</p> : null}
        <div className={recruitmentStyles.formActions}>
          <button type="submit" className={recruitmentStyles.primaryButton} disabled={isSubmitting}>
            {isSubmitting ? '저장 중…' : mode.kind === 'edit' ? '수정 저장' : '모집글 등록'}
          </button>
        </div>
      </form>
    </main>
  )
}

function SelectedProgram({ program, onClear }: { program: ProgramSummary; onClear?: () => void }) {
  return (
    <div className={recruitmentStyles.programCard}>
      <span className={recruitmentStyles.programTag}>기업마당 · {programStatusLabels[program.status]}</span>
      <p className={recruitmentStyles.programTitle}>{program.title}</p>
      <p className={recruitmentStyles.programMeta}>
        {program.organization} · 신청 기간 {program.applicationPeriod}
      </p>
      {onClear ? (
        <div className={recruitmentStyles.programLinks}>
          <button type="button" className={recruitmentStyles.linkButton} onClick={onClear}>공고 변경</button>
        </div>
      ) : null}
    </div>
  )
}
