/** 요금제 한 벌입니다. 요금제 화면과 도우미가 같은 값을 읽어 두 곳의 안내가 어긋나지 않게 합니다. */
// 현재 제공 기능과 출시 준비 방향을 구분합니다. 유료 가격·결제 정책은 아직 확정하지 않습니다.
export const pricingPlans = [
  {
    id: 'free',
    label: 'FREE',
    name: '무료',
    status: '지금 이용 가능',
    description: '우리 기업에 맞는 지원사업을 찾고, 공고의 조건부터 확인하고 싶다면.',
    price: '0원',
    priceNote: '현재 공개 검색 이용 요금',
    featureHeading: '지금 제공하는 기능',
    features: ['지원사업 검색', '입력한 기업 조건으로 자격 조건 확인', '기업마당 공고 원문 질문과 답변'],
    footerNote: '현재 공개 검색은 로그인 없이 이용할 수 있습니다.',
    isAvailable: true,
    isFeatured: false,
  },
  {
    id: 'pro',
    label: 'PRO',
    name: '프로',
    status: '출시 예정',
    description: '관심 공고와 기업 정보를 모아, 지원사업 검토를 이어가고 싶다면.',
    price: '가격 공개 예정',
    priceNote: '유료 요금제 · 출시 준비 중',
    featureHeading: '출시 준비 방향',
    features: ['관심 공고 관리', '기업 프로필을 활용한 탐색', '검토한 공고의 이력 관리'],
    footerNote: '가격과 제공 범위는 출시 시 안내합니다.',
    isAvailable: false,
    isFeatured: true,
  },
  {
    id: 'team',
    label: 'TEAM',
    name: '팀',
    status: '출시 예정',
    description: '함께할 기업을 살펴보고, 지원사업을 중심으로 협업을 준비하고 싶다면.',
    price: '가격 공개 예정',
    priceNote: '유료 요금제 · 출시 준비 중',
    featureHeading: '출시 준비 방향',
    features: ['기업 프로필 기반 파트너 탐색', '공고별 파트너 모집', '협업을 위한 기업 간 제안'],
    footerNote: '가격과 제공 범위는 출시 시 안내합니다.',
    isAvailable: false,
    isFeatured: false,
  },
] as const

export type PricingPlan = (typeof pricingPlans)[number]
