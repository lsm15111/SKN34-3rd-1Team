import { Fragment } from 'react'
import { Link } from 'react-router'

import { appPaths, publicPaths } from '../../../shared/routes/appPaths'

import { pricingPageStyles } from './PricingPage.styles'

// 지금은 모든 기능이 베타로 열려 있습니다. 유료 전환 시점의 예정 가격을 함께 적어 미리 알립니다.
const plans = [
  {
    id: 'free',
    label: 'FREE',
    name: '무료',
    status: '계속 무료',
    description: '우리 기업에 맞는 지원사업을 찾고, 공고의 조건부터 확인하고 싶다면.',
    price: '0원',
    priceNote: '로그인 없이 바로 검색',
    featureHeading: '포함된 기능',
    features: [
      '자연어 지원사업 검색과 추천 이유·점수',
      '입력한 기업 조건으로 자격 조건 확인',
      '공고 원문을 근거로 한 질문과 답변',
      '관심 공고함에 공고 담기',
    ],
    footerNote: '로그인하면 관심 공고함과 검색 기록이 계정에 남습니다.',
    isFree: true,
    isFeatured: false,
  },
  {
    id: 'pro',
    label: 'PRO',
    name: '프로',
    status: '베타 기간 무료',
    description: '찾은 공고를 실제 신청까지 끌고 가고 싶다면.',
    price: '베타 기간 0원',
    priceNote: '정식 전환 시 월 39,000원 예정 · 연 결제 월 33,000원',
    featureHeading: '무료에 더해',
    features: [
      '기업 정보 기반 맞춤 공고 리포트',
      '중복 지원·수혜 여부 검토',
      '공식 첨부에서 찾은 신청 문서 작성',
    ],
    footerNote: '베타 기간에는 결제 없이 그대로 쓰실 수 있습니다.',
    isFree: false,
    isFeatured: true,
  },
  {
    id: 'team',
    label: 'TEAM',
    name: '팀',
    status: '베타 기간 무료',
    description: '컨소시엄으로 함께 지원할 기업을 찾고 싶다면.',
    price: '베타 기간 0원',
    priceNote: '정식 전환 시 월 99,000원 예정 · 연 결제 월 83,000원',
    featureHeading: '프로에 더해',
    features: [
      '기업 프로필로 파트너 찾기',
      '공고별 파트너 모집글 등록',
      '참여 제안 주고받기와 담당자 연락처 교환',
    ],
    footerNote: '기업 정보를 등록한 계정끼리 연결합니다.',
    isFree: false,
    isFeatured: false,
  },
] as const

const searchSteps = [
  {
    number: '01',
    title: '필요한 지원사업 찾기',
    description: '지역, 업종, 지원 목적을 자연스럽게 입력하고 관련 공고를 찾아보세요.',
  },
  {
    number: '02',
    title: '기업 조건과 비교하기',
    description: '입력한 조건을 바탕으로 공고의 자격 조건과 추가 확인이 필요한 부분을 살펴보세요.',
  },
  {
    number: '03',
    title: '원문을 근거로 질문하기',
    description: '기업마당 공고 상세에서 궁금한 내용을 질문하고, 답변의 근거를 원문과 함께 확인하세요.',
  },
] as const

const frequentlyAskedQuestions = [
  {
    question: '무료 요금제에서는 무엇을 할 수 있나요?',
    answer: '지원사업 검색, 입력한 기업 조건을 바탕으로 한 조건 확인, 기업마당 공고 상세에서의 원문 근거 질문을 이용할 수 있습니다. 현재 공개 검색은 로그인 없이 시작할 수 있습니다.',
  },
  {
    question: '프로와 팀 기능도 지금 쓸 수 있나요?',
    answer: '네. 베타 기간에는 세 요금제의 기능을 결제 없이 모두 쓰실 수 있습니다. 카드에 적은 가격은 정식 전환 시 예정 가격이며, 전환 시점과 최종 가격은 미리 안내합니다.',
  },
  {
    question: '기업 프로필과 파트너 모집은 실제로 동작하나요?',
    answer: '네. 사업자등록번호로 기업을 등록하면 프로필이 계정에 저장되고, 접수 중인 공고에 모집글을 올려 참여 제안을 주고받을 수 있습니다. 수락된 제안에서만 상대 담당자 연락처를 공개합니다.',
  },
  {
    question: 'AI가 지원 자격이나 선정을 보장하나요?',
    answer: '아니요. AI의 조건 확인과 답변은 공고를 살펴보기 위한 참고 정보입니다. 지원 자격, 접수 상태와 신청 방법은 공고 원문 및 담당 기관에서 최종 확인해야 합니다.',
  },
] as const

type PricingPageLayout = 'public' | 'workspace'

const pricingTitle = '기업의 다음 단계에 맞는 요금제'

/** 글자 공간과 접근 가능한 제목은 유지하고 시각적인 글자만 순서대로 나타냅니다. */
function PricingTitle() {
  let characterIndex = 0
  return <h1 className={pricingPageStyles.title} id="pricing-title" aria-label={pricingTitle}>
    <span aria-hidden="true">
      {pricingTitle.split(' ').map((word, wordIndex) => {
        if (wordIndex > 0) characterIndex += 1
        return <Fragment key={wordIndex}>
          {wordIndex > 0 ? ' ' : null}
          <span className={pricingPageStyles.titleWord}>
            {Array.from(word).map((character, index) => <span key={index} data-pricing-title-character=""
              className={pricingPageStyles.titleCharacter}
              style={{ animationDelay: `${180 + characterIndex++ * 75}ms` }}>{character}</span>)}
          </span>
        </Fragment>
      })}
    </span>
  </h1>
}

/**
 * 결제 기능 없이 현재 공개 기능과 출시 예정 요금제를 안내합니다. 로그인 전에는 헤더 아래 공개 페이지로,
 * 로그인 뒤에는 사이드바 안에서 같은 내용을 보여 주며 검색 진입 버튼만 각 세계의 검색 화면으로 향합니다.
 */
export function PricingPage({ layout = 'public' }: { layout?: PricingPageLayout }) {
  const searchPath = layout === 'workspace' ? appPaths.chat : publicPaths.landing
  const faqPath = layout === 'workspace' ? appPaths.faq : publicPaths.faq
  return (
    <main className={pricingPageStyles.page}>
      <section className={pricingPageStyles.hero} aria-labelledby="pricing-title">
        <PricingTitle />
        <p className={pricingPageStyles.description}>
          지원사업 탐색은 계속 무료입니다.
          <br />
          신청 준비와 파트너 협업은 베타 기간 동안 결제 없이 열어 두었습니다.
        </p>
      </section>

      <section className={pricingPageStyles.plansSection} aria-labelledby="pricing-plans-title">
        <h2 className={pricingPageStyles.plansHeading} id="pricing-plans-title">
          지금 시작하고, 필요한 만큼 확장하세요
        </h2>
        <div className={pricingPageStyles.plansGrid}>
          {plans.map((plan) => {
            const cardTone = plan.isFeatured
              ? pricingPageStyles.featuredCard
              : pricingPageStyles.regularCard
            const mutedTone = plan.isFeatured
              ? pricingPageStyles.featuredMuted
              : pricingPageStyles.regularMuted
            const iconTone = plan.isFeatured
              ? pricingPageStyles.featuredIcon
              : pricingPageStyles.regularIcon

            return (
              <article
                className={`${pricingPageStyles.planCard} ${cardTone}`}
                aria-labelledby={`pricing-${plan.id}-title`}
                key={plan.id}
              >
                <div className={pricingPageStyles.planTop}>
                  <p className={`${pricingPageStyles.planEyebrow} ${mutedTone}`}>{plan.label}</p>
                  <span className={`${pricingPageStyles.planStatus} ${plan.isFeatured
                    ? pricingPageStyles.featuredStatus
                    : pricingPageStyles.regularStatus}`}
                  >
                    {plan.status}
                  </span>
                </div>
                <h3 className={pricingPageStyles.planTitle} id={`pricing-${plan.id}-title`}>
                  {plan.name}
                </h3>
                <p className={`${pricingPageStyles.planDescription} ${mutedTone}`}>
                  {plan.description}
                </p>
                <div className={pricingPageStyles.priceBlock}>
                  <p className={plan.isFree ? pricingPageStyles.freePrice : pricingPageStyles.pendingPrice}>
                    {plan.price}
                  </p>
                  <p className={`${pricingPageStyles.priceNote} ${mutedTone}`}>{plan.priceNote}</p>
                </div>
                <div className={`${pricingPageStyles.divider} ${plan.isFeatured
                  ? pricingPageStyles.featuredDivider
                  : pricingPageStyles.regularDivider}`}
                  aria-hidden="true"
                />
                <p className={pricingPageStyles.featureHeading}>{plan.featureHeading}</p>
                <ul className={pricingPageStyles.featureList}>
                  {plan.features.map((feature) => (
                    <li className={pricingPageStyles.featureItem} key={feature}>
                      <svg
                        className={`${pricingPageStyles.featureIcon} ${iconTone}`}
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                        focusable="false"
                      >
                        <path d="m5 12 4 4L19 6" />
                      </svg>
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>
                <div className={pricingPageStyles.planFooter}>
                  <Link className={`${pricingPageStyles.planButton} ${pricingPageStyles.availableButton}`} to={searchPath}>
                    무료로 시작하기
                  </Link>
                  <p className={`${pricingPageStyles.footerNote} ${mutedTone}`}>{plan.footerNote}</p>
                </div>
              </article>
            )
          })}
        </div>
        <p className={pricingPageStyles.releaseNote}>
          지금은 세 요금제의 기능을 모두 결제 없이 쓰실 수 있습니다. 표시한 가격은 정식 전환 시 예정 가격입니다.
        </p>
      </section>

      <section className={pricingPageStyles.valueSection} aria-labelledby="pricing-value-title">
        <div>
          <p className={pricingPageStyles.sectionEyebrow}>현재 무료로 이용할 수 있어요</p>
          <h2 className={pricingPageStyles.sectionHeading} id="pricing-value-title">
            찾고, 확인하고, 질문하세요
          </h2>
        </div>
        <div className={pricingPageStyles.valueGrid}>
          {searchSteps.map((step) => (
            <div className={pricingPageStyles.valueItem} key={step.number}>
              <span className={pricingPageStyles.valueNumber} aria-hidden="true">{step.number}</span>
              <h3 className={pricingPageStyles.valueTitle}>{step.title}</h3>
              <p className={pricingPageStyles.valueDescription}>{step.description}</p>
            </div>
          ))}
        </div>
      </section>

      <section className={pricingPageStyles.faqSection} aria-labelledby="pricing-faq-title">
        <div className={pricingPageStyles.faqHeader}>
          <h2 className={pricingPageStyles.sectionHeading} id="pricing-faq-title">자주 묻는 질문</h2>
          <p className={pricingPageStyles.faqDescription}>
            이용 전에 궁금한 점을 확인하세요. 화면 사용법은 <Link className={pricingPageStyles.faqLink} to={faqPath}>자주 묻는 질문</Link>에 모아 두었습니다.
          </p>
        </div>
        <div className={pricingPageStyles.faqList}>
          {frequentlyAskedQuestions.map((faq) => (
            <details className={pricingPageStyles.faqItem} key={faq.question}>
              <summary className={pricingPageStyles.faqQuestion}>
                <span className={pricingPageStyles.faqQuestionText}>{faq.question}</span>
                <svg
                  className={pricingPageStyles.faqIcon}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  aria-hidden="true"
                  focusable="false"
                >
                  <path d="M12 5v14M5 12h14" />
                </svg>
              </summary>
              <p className={pricingPageStyles.faqAnswer}>{faq.answer}</p>
            </details>
          ))}
        </div>
      </section>

      <section className={pricingPageStyles.closingSection} aria-labelledby="pricing-start-title">
        <h2 className={pricingPageStyles.sectionHeading} id="pricing-start-title">
          다음 기회가 될 공고를 만나보세요
        </h2>
        <p className={pricingPageStyles.closingDescription}>
          우리 기업의 지역, 업종, 지원 목적부터 이야기해 주세요.
          <br />
          지금 제공하는 검색 기능으로 탐색을 시작할 수 있습니다.
        </p>
        <Link className={pricingPageStyles.closingButton} to={searchPath}>
          지원사업 찾기 시작하기
          <svg
            className={pricingPageStyles.arrowIcon}
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
            focusable="false"
          >
            <path d="M5 12h14m-6-6 6 6-6 6" />
          </svg>
        </Link>
      </section>
    </main>
  )
}
