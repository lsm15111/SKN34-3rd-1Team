-- GovBiz 개발용 목데이터
--
-- 파트너 모집 화면을 채우는 예시 공고·기업·모집글·제안입니다. 실제 기업·공고가 아니며 사업자등록번호·도메인·이메일은 모두 가상 값입니다.
-- 저장소 루트에서 MySQL 컨테이너에 흘려 넣습니다.
--
--   docker exec -i govbiz-mysql-1 sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < infrastructure/dev-seed.sql
--
-- 같은 파일을 몇 번 실행해도 됩니다. 시드 계정과 FK CASCADE로 딸린 기업·협업 설정·모집글·제안을 먼저 지우고 오늘 기준 날짜로
-- 다시 만들기 때문에 UNIQUE 충돌이 없고, 며칠 지나 대기 제안이 만료로 바뀌면 다시 실행해 처음 모양으로 되돌립니다.
-- 시드 계정이 아닌 회원의 행은 건드리지 않지만, 그 회원이 시드 모집글에 보낸 제안은 모집글과 함께 지워집니다.
--
-- 로그인: 아래 모든 계정의 비밀번호는 개발 로그인 비밀번호(govbiz-admin1)입니다. company@govbiz.local이 "내 기업"(그루브데이터)이고,
-- 헤더의 `개발 로그인 · 기업` 버튼도 같은 계정으로 들어갑니다. coop@<기업>.example로 로그인하면 상대 기업 쪽 화면을 볼 수 있습니다.
--
-- 예시 공고는 제공처 코드 DEMO로 넣습니다. 기업마당 동기화는 출처별 스냅샷이라 DEMO 행을 지우지 않고, 검색 카탈로그는 색인 준비
-- 제공처만 보여 주므로 이 공고는 모집글에서만 보입니다.

SET NAMES utf8mb4;
SET @today = CURDATE();
SET @now = NOW(6);
-- BCrypt('govbiz-admin1')
SET @password_hash = '$2a$10$U9JjTNkKKhLJk1.OGl0Ste7lvc0MK2F7KRXNphhSkZJJA0XcLgHvC';

-- ---------------------------------------------------------------------------------------------------------------------
-- 1. 지난 시드 제거 (기업·협업 설정·모집글·제안은 FK CASCADE)
-- ---------------------------------------------------------------------------------------------------------------------
DELETE FROM account WHERE email IN (
    'company@govbiz.local',
    'coop@faconnect.example', 'coop@gaonmetalworks.example', 'coop@visionsquarelab.example', 'coop@metaflowworks.example',
    'coop@novainspect.example', 'coop@retailnode.example', 'coop@ecometer.example', 'coop@farmbridgelab.example', 'coop@blueharborfood.example'
);

-- ---------------------------------------------------------------------------------------------------------------------
-- 2. 예시 공고 7건 (DEMO 출처, 있으면 갱신). 접수 기간은 오늘 기준 상대값
-- ---------------------------------------------------------------------------------------------------------------------
SET @sd2_start = @today - INTERVAL 13 DAY;
SET @sd2_end = @today + INTERVAL 19 DAY;
SET @reorg_start = @today - INTERVAL 9 DAY;
SET @reorg_end = @today + INTERVAL 34 DAY;
SET @store_start = @today - INTERVAL 15 DAY;
SET @store_end = @today + INTERVAL 20 DAY;
SET @ax_start = @today - INTERVAL 155 DAY;
SET @ax_end = @today - INTERVAL 126 DAY;
SET @always_open = '상시 접수 (예산 소진 시까지)';

INSERT INTO support_program (
    source_code, source_program_id, title, organization, summary, categories, regions, target_description,
    application_period_raw, application_start_date, application_end_date, source_url, source_sort_timestamp, is_source_present
) VALUES
(
    'DEMO', 'PBLN_000000000125950',
    '2026년 2차 스마트공장 공급기업 역량진단 참여기업 모집 공고', '중소기업기술정보진흥원',
    '스마트공장 공급기업의 기술·사업 역량을 체계적으로 진단하고, 진단 결과를 바탕으로 개선 방향을 제시합니다. 진단 비용의 80%를 정부가 지원합니다.',
    JSON_ARRAY('기술', '경영'), JSON_ARRAY('전국'),
    '중소·중견기업 중 사업관리시스템(www.smart-factory.kr)에 등록된 스마트제조, 스마트서비스, 스마트공방 공급기업',
    CONCAT(DATE_FORMAT(@sd2_start, '%Y. %c. %e.'), ' ~ ', DATE_FORMAT(@sd2_end, '%Y. %c. %e.'), ' 18:00'), @sd2_start, @sd2_end,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000125950', DATE_FORMAT(@sd2_start, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000126094',
    '2026년 4분기(55차) 사업재편지원사업 참여기업 모집 공고', '한국경영혁신중소기업협회',
    '혁신형 중소기업의 자발적·선제적 사업재편 활성화를 위하여 신산업·탄소중립·디지털 전환·공급망 안정 분야 사업의 사업재편을 지원합니다.',
    JSON_ARRAY('경영', '사업화'), JSON_ARRAY('전국'),
    '업력 4년 이상의 중소·중견기업 중 구조개혁과 사업혁신을 동시에 준비 중인 기업',
    CONCAT(DATE_FORMAT(@reorg_start, '%Y. %c. %e.'), ' ~ ', DATE_FORMAT(@reorg_end, '%Y. %c. %e.'), ' 18:00'), @reorg_start, @reorg_end,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000126094', DATE_FORMAT(@reorg_start, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000116004',
    '스마트 공장 수준 확인(2026년 스마트 제조혁신 지원사업 통합 공고)', '스마트제조혁신추진단',
    '디지털·인공지능 전환을 통한 중소·중견기업의 경쟁력 제고를 목표로, 자체 역량으로 스마트공장을 구축했거나 고도화를 추진 중인 기업의 스마트화 수준을 진단합니다. 수준확인서와 진단보고서를 제공하며 확인 비용은 전액 지원합니다.',
    JSON_ARRAY('기술'), JSON_ARRAY('전국'),
    '자체역량으로 스마트공장 구축한 기업(정부 스마트공장지원사업 미참여), 자발적 고도화 추진으로 스마트화 수준 상승이 기대되는 기업, 스마트공장 수준확인서 유효기간 만료기업',
    @always_open, NULL, NULL,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000116004', DATE_FORMAT(@today, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000116007',
    '공급기업 역량진단(2026년 스마트 제조혁신 지원사업 통합 공고)', '스마트제조혁신추진단',
    '스마트제조 공급기업의 기술력과 사업 수행 역량을 진단해 공급기업 풀의 품질을 관리하고, 기업별 개선 과제를 제시합니다.',
    JSON_ARRAY('기술', '경영'), JSON_ARRAY('전국'),
    '사업관리시스템(www.smart-factory.kr)에 등록된 스마트제조·스마트서비스 공급기업',
    @always_open, NULL, NULL,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000116007', DATE_FORMAT(@today, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000119308',
    '2026년 스마트상점 기술보급사업 참여 구입형·렌탈형·S/W 소상공인형 모집 공고', '소상공인시장진흥공단',
    '소상공인 점포의 운영 효율화와 디지털 전환을 위해 스마트 기술 도입 비용을 지원합니다. 구입형은 일반기술 최대 500만원, 배리어프리 기술 최대 700만원을 지원합니다.',
    JSON_ARRAY('내수', '기술'), JSON_ARRAY('전국'),
    '사업자등록증을 보유하고 정상 영업 중인 소상공인. 등록된 기술공급기업과 매칭한 뒤 신청합니다.',
    CONCAT(DATE_FORMAT(@store_start, '%Y. %c. %e.'), ' ~ ', DATE_FORMAT(@store_end, '%Y. %c. %e.'), ' 18:00'), @store_start, @store_end,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000119308', DATE_FORMAT(@store_start, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000119500',
    '2026년 기술개발제품 공공기관 실증지원 사업 공고', '중소벤처기업부',
    '공공기관 현장에 중소기업 기술개발제품을 설치해 성능시험 등 실증을 지원하고 공공구매로 연결합니다. 제품당 현장실증비를 최대 3천만원, 실증 비용의 80% 범위에서 지원합니다.',
    JSON_ARRAY('기술개발(R&D)', '내수'), JSON_ARRAY('전국'),
    '기술개발제품을 보유한 중소기업과 실증 현장을 제공하는 공공기관',
    @always_open, NULL, NULL,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000119500', DATE_FORMAT(@today, '%Y-%m-%d'), TRUE
),
(
    'DEMO', 'PBLN_000000000120085',
    '2026년 AX 원스톱 바우처 지원사업 수요기업 모집 공고', '정보통신산업진흥원',
    'AI·클라우드·데이터를 바우처 형태로 통합 지원하여 중소기업 등의 인공지능 전환(AX)을 촉진합니다. 260억원 규모로 지원하며 단계평가를 통해 2차년도 지원 여부를 결정합니다.',
    JSON_ARRAY('기술', '사업화'), JSON_ARRAY('전국'),
    '제조·의료·농업·환경·방송·안전 등 분야의 중소기업·중견기업·대기업. AI 공급기업과 컨소시엄을 구성해 신청합니다.',
    CONCAT(DATE_FORMAT(@ax_start, '%Y. %c. %e.'), ' ~ ', DATE_FORMAT(@ax_end, '%Y. %c. %e.'), ' 18:00'), @ax_start, @ax_end,
    'https://www.bizinfo.go.kr/sii/siia/selectSIIA200Detail.do?pblancId=PBLN_000000000120085', DATE_FORMAT(@ax_start, '%Y-%m-%d'), TRUE
)
AS new
ON DUPLICATE KEY UPDATE
    title = new.title, organization = new.organization, summary = new.summary, categories = new.categories, regions = new.regions,
    target_description = new.target_description, application_period_raw = new.application_period_raw,
    application_start_date = new.application_start_date, application_end_date = new.application_end_date, source_url = new.source_url,
    source_sort_timestamp = new.source_sort_timestamp, is_source_present = TRUE, last_seen_at = CURRENT_TIMESTAMP(6);

SET @p_sd2 = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000125950');
SET @p_reorg = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000126094');
SET @p_level = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000116004');
SET @p_sdbase = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000116007');
SET @p_store = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000119308');
SET @p_poc = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000119500');
SET @p_ax = (SELECT id FROM support_program WHERE source_code = 'DEMO' AND source_program_id = 'PBLN_000000000120085');

-- ---------------------------------------------------------------------------------------------------------------------
-- 3. 기업 회원 계정 10개 (내 기업 1 + 파트너 기업 9), 기업, 협업·파트너 설정
-- ---------------------------------------------------------------------------------------------------------------------
-- 내 기업: company@govbiz.local (개발 로그인 · 기업 버튼과 같은 계정)
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('company@govbiz.local', @password_hash, 'USER', @now, @now);
SET @acc_me = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_me, '2208800042', '그루브데이터 주식회사', '계속사업자', '01', '서울특별시', '정보통신업', 2022, 'https://groovedata.example', @now, @now, @now);
SET @co_me = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_me, JSON_ARRAY('PARTICIPANT'), JSON_ARRAY('기술개발(R&D)', '사업화', '기술'),
    '제조 설비 로그와 ERP 데이터를 묶어 원가·불량 지표를 만드는 데이터 분석 팀입니다. 사업재편·스마트공장 과제에 참여기관으로 들어가 성과지표 설계와 분석을 맡습니다.',
    JSON_ARRAY('제조 데이터 분석', '성과지표 설계', '설비 로그 파이프라인', '스마트공장 고도화 참여 3건'), @now, @now
);

-- 주식회사 에프에이커넥트
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@faconnect.example', @password_hash, 'USER', @now, @now);
SET @acc_faconnect = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_faconnect, '1348100211', '주식회사 에프에이커넥트', '계속사업자', '01', '경기도', '정보통신업', 2016, 'https://faconnect.example', @now, @now, @now);
SET @co_faconnect = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_faconnect, JSON_ARRAY('LEAD', 'PARTICIPANT'), JSON_ARRAY('기술', '기술개발(R&D)', '내수'),
    '사출·프레스 공정용 MES를 공급하는 스마트제조 공급기업입니다. 경기·충남 사출 공장 11개소 레퍼런스가 있습니다.',
    JSON_ARRAY('MES 구축', 'OPC UA 설비 연동', '생산 실적 집계', '공급기업 역량진단 대응'), @now, @now
);

-- 주식회사 가온메탈웍스
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@gaonmetalworks.example', @password_hash, 'USER', @now, @now);
SET @acc_gaon = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_gaon, '6098201734', '주식회사 가온메탈웍스', '계속사업자', '01', '경상남도', '제조업', 2009, 'https://gaonmetalworks.example', @now, @now, @now);
SET @co_gaon = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_gaon, JSON_ARRAY('LEAD'), JSON_ARRAY('사업화', '경영', '기술'),
    '창원의 정밀 절삭가공 기업입니다. 내연기관 부품 비중을 낮추는 사업재편을 준비하고 있으며 주관기업 역할을 맡습니다.',
    JSON_ARRAY('정밀 절삭가공', '탄소저감 공정 전환 계획', '에너지 사용량 월별 관리', '사업재편 계획서 총괄'), @now, @now
);

-- 비전스퀘어랩 주식회사 (이메일 미인증)
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@visionsquarelab.example', @password_hash, 'USER', NULL, @now);
SET @acc_vision = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_vision, '5148100388', '비전스퀘어랩 주식회사', '계속사업자', '01', '대구광역시', '제조업', 2015, 'https://visionsquarelab.example', @now, @now, @now);
SET @co_vision = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_vision, JSON_ARRAY('PARTICIPANT'), JSON_ARRAY('기술', '기술개발(R&D)'),
    '머신비전 검사장비를 만드는 대구 성서공단 제조기업입니다. 자체 예산으로 MES와 검사 데이터 수집 체계를 구축했습니다.',
    JSON_ARRAY('머신비전 검사장비', '검사 데이터 수집', '현장 데이터 공개 가능'), @now, @now
);

-- 메타플로우웍스 주식회사
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@metaflowworks.example', @password_hash, 'USER', @now, @now);
SET @acc_metaflow = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_metaflow, '3148100477', '메타플로우웍스 주식회사', '계속사업자', '01', '대전광역시', '정보통신업', 2019, 'https://metaflowworks.example', @now, @now, @now);
SET @co_metaflow = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_metaflow, JSON_ARRAY('LEAD', 'PARTICIPANT'), JSON_ARRAY('기술개발(R&D)', '기술', '사업화'),
    '자동차 부품 표면 결함 검사 AI 모델을 개발합니다. 학습 데이터 12만 장과 GPU 학습 환경을 갖추고 있습니다.',
    JSON_ARRAY('결함 검사 AI 모델', '학습 데이터 관리 체계', 'GPU 학습 환경', '설비 이상 예지', '품질 예측'), @now, @now
);

-- 노바인스펙트 주식회사
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@novainspect.example', @password_hash, 'USER', @now, @now);
SET @acc_nova = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_nova, '1318100592', '노바인스펙트 주식회사', '계속사업자', '01', '인천광역시', '제조업', 2013, 'https://novainspect.example', @now, @now, @now);
SET @co_nova = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_nova, JSON_ARRAY('PARTICIPANT'), JSON_ARRAY('기술', '내수'),
    '인천 남동공단에서 검사 자동화 설비를 제작합니다. 업력 13년이고 최근 3년 납품 실적 자료를 바로 제공할 수 있습니다.',
    JSON_ARRAY('검사 자동화 설비 제작', '설비 납품 실적 3년', '자체 공장 보유'), @now, @now
);

-- 주식회사 리테일노드
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@retailnode.example', @password_hash, 'USER', @now, @now);
SET @acc_retail = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_retail, '2118100615', '주식회사 리테일노드', '계속사업자', '01', '서울특별시', '정보통신업', 2021, 'https://retailnode.example', @now, @now, @now);
SET @co_retail = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_retail, JSON_ARRAY('LEAD'), JSON_ARRAY('내수', '사업화', '판로ㆍ해외진출'),
    '무인 매장 운영 솔루션을 만드는 스마트상점 기술공급기업입니다. 키오스크 연동 재고관리와 대기열 안내를 공급합니다.',
    JSON_ARRAY('키오스크 연동 재고관리', '대기열 안내', '배리어프리 기술', '스마트상점 기술공급기업 등록'), @now, @now
);

-- 에코미터솔루션 주식회사
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@ecometer.example', @password_hash, 'USER', @now, @now);
SET @acc_eco = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_eco, '6058802431', '에코미터솔루션 주식회사', '계속사업자', '01', '부산광역시', '전문, 과학 및 기술 서비스업', 2016, 'https://ecometer.example', @now, @now, @now);
SET @co_eco = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_eco, JSON_ARRAY('PARTICIPANT'), JSON_ARRAY('기술', '기술개발(R&D)', '내수'),
    '건물 에너지 사용량을 계측해 낭비 구간을 찾아 주는 솔루션을 만듭니다. 성능인증을 받았고 공공 실증 레퍼런스를 준비 중입니다.',
    JSON_ARRAY('에너지 계측기 설치', '에너지 분석 리포트', '성능인증 보유'), @now, @now
);

-- 팜브릿지랩 주식회사
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@farmbridgelab.example', @password_hash, 'USER', @now, @now);
SET @acc_farm = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_farm, '4038100729', '팜브릿지랩 주식회사', '계속사업자', '01', '전북특별자치도', '농업, 임업 및 어업', 2017, 'https://farmbridgelab.example', @now, @now, @now);
SET @co_farm = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_farm, JSON_ARRAY('LEAD', 'PARTICIPANT'), JSON_ARRAY('기술', '기술개발(R&D)'),
    '스마트팜 환경 데이터를 다뤄 온 팀이고 설비 데이터 수집 모듈을 제조 쪽으로 넓히고 있습니다.',
    JSON_ARRAY('환경 데이터 수집', 'PLC 통신 개발', '엣지 게이트웨이 양산'), @now, @now
);

-- 블루하버푸드 주식회사 (이메일 미인증)
INSERT INTO account (email, password_hash, role, email_verified_at, terms_agreed_at) VALUES ('coop@blueharborfood.example', @password_hash, 'USER', NULL, @now);
SET @acc_blue = LAST_INSERT_ID();
INSERT INTO company (account_id, business_number, company_name, business_status, business_status_code, region, industry, founded_year, homepage_url, business_verified_at, created_at, updated_at)
VALUES (@acc_blue, '4168100834', '블루하버푸드 주식회사', '계속사업자', '01', '전라남도', '제조업', 2018, 'https://blueharborfood.example', @now, @now, @now);
SET @co_blue = LAST_INSERT_ID();
INSERT INTO company_partner_profile (company_id, roles, interest_areas, introduction, capabilities, created_at, updated_at) VALUES (
    @co_blue, JSON_ARRAY('PARTICIPANT'), JSON_ARRAY('기술', '수출'),
    '전남의 수산 가공 기업입니다. 가공 라인에 AI 검사 기능을 붙이는 R&D 파트너를 찾고 있습니다.',
    JSON_ARRAY('수산 가공 라인 운영', 'HACCP 인증'), @now, @now
);

-- ---------------------------------------------------------------------------------------------------------------------
-- 4. 모집글 10건. 모집 마감일·작성일은 오늘 기준 상대값 (모집 중 8, 모집 마감일 경과 1, 공고 접수 종료 1)
-- ---------------------------------------------------------------------------------------------------------------------
-- 101 에프에이커넥트 · 2차 역량진단 · 참여기관 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_faconnect, @co_faconnect, @p_sd2, '스마트공장 공급기업 역량진단, 설비 데이터 연동 맡을 참여기관 찾습니다', CONCAT(
    '사출·프레스 공정용 MES를 공급하는 스마트제조 공급기업입니다. 사업관리시스템에는 2019년에 스마트제조 공급기업으로 등록했고, 이번 2차 역량진단에 신청하면서 진단 항목 가운데 설비 데이터 연동 영역을 함께 준비할 참여기관을 찾습니다.', '\n\n',
    '보유 레퍼런스는 경기·충남 지역 사출 공장 11개소이며 설비 프로토콜은 OPC UA와 Modbus TCP를 주로 씁니다. 저희가 MES 화면과 실적 집계, 진단 대응 문서를 맡고, 참여기관에서는 엣지 게이트웨이 구성과 수집 주기 설계, 통신 예외 처리를 맡아 주시면 좋겠습니다.', '\n\n',
    '진단 일정은 접수 마감 뒤 2~3주 사이로 예상합니다. 사전 미팅은 온라인 2회 정도 생각하고 있고, 모집 마감 전에 현장 1곳(안산)을 같이 보는 일정이 있습니다.', '\n\n',
    '역할과 비용 분담은 제안 주시면 협의하겠습니다. 참고로 진단 대응 과정에서 발생하는 인건비는 저희가 7 : 참여기관 3 정도로 잡아 두었고, 확정 전이라 조정 가능합니다.', '\n\n',
    '연동 실적이 많지 않아도 괜찮습니다. PLC 태그 정의서를 읽고 수집 항목을 정리해 본 경험만 있으면 충분합니다.'
), 'LEAD', 'PARTICIPANT', 1, '경기', 3, JSON_ARRAY('OPC UA 설비 연동', '엣지 게이트웨이 구축', '데이터 수집 주기 설계'),
@today + INTERVAL 15 DAY, NULL, TIMESTAMP(@today - INTERVAL 9 DAY, '09:20:00'), TIMESTAMP(@today - INTERVAL 9 DAY, '09:20:00'));
SET @r101 = LAST_INSERT_ID();

-- 102 가온메탈웍스 · 사업재편 · 참여기업 2곳
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_gaon, @co_gaon, @p_reorg, '사업재편 승인 준비 중입니다. 탄소저감 공정 보유한 제조기업 2곳 찾습니다', CONCAT(
    '창원에서 정밀 절삭가공을 하는 중소기업입니다. 자동차 내연기관 부품 비중이 아직 60% 가까이 되어 사업재편 승인을 준비하고 있고, 같은 방향으로 전환을 고민하는 제조기업 2곳과 함께 신청하려 합니다.', '\n\n',
    '저희가 주관기업으로 계획서 총괄과 회계 자료 정리를 맡습니다. 참여기업에는 탄소저감 공정 전환 계획과 에너지 사용량 실적 자료를 요청드릴 예정이고, 컨설팅 비용은 저희가 먼저 집행한 뒤 선정 후 정산하는 방식을 생각하고 있습니다.', '\n\n',
    '업종은 금속가공·표면처리·열처리 쪽이면 서로 설명하기 좋겠습니다만, 공정 전환 논리가 분명하면 업종은 크게 따지지 않겠습니다.', '\n\n',
    '공고 접수 마감 2주 전까지는 참여기업을 확정해야 합니다. 제안 주시면 바로 온라인 미팅 잡겠습니다.'
), 'LEAD', 'PARTICIPANT', 2, '전국', 4, JSON_ARRAY('탄소저감 공정 보유', '설비 투자 계획 수립', '에너지 사용량 데이터 보유'),
@today + INTERVAL 28 DAY, NULL, TIMESTAMP(@today - INTERVAL 7 DAY, '14:05:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '14:05:00'));
SET @r102 = LAST_INSERT_ID();

-- 103 비전스퀘어랩 · 수준 확인 · 주관기관 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_vision, @co_vision, @p_level, '수준확인 신청 전 현장 진단을 함께할 주관기관을 찾습니다', CONCAT(
    '머신비전 검사장비를 만드는 회사입니다. 3년 전 자체 예산으로 MES와 검사 데이터 수집 체계를 구축했는데 정부 스마트공장 지원사업에는 참여한 적이 없어 수준확인 대상이 됩니다.', '\n\n',
    '문제는 사내에 수준확인 항목을 정리해 본 사람이 없다는 점입니다. 진단 항목별로 현재 수준을 정리하고 근거 자료를 붙이는 작업을 함께해 주실 주관기관을 찾습니다.', '\n\n',
    '저희는 참여기관으로 들어가고, 현장 데이터와 설비 목록, 시스템 화면은 모두 공개할 수 있습니다. 대구 성서공단에 공장이 있고 방문은 언제든 협의 가능합니다.'
), 'PARTICIPANT', 'LEAD', 1, '대구', NULL, JSON_ARRAY('스마트공장 수준진단 경험', '제조 현장 컨설팅'),
@today + INTERVAL 9 DAY, NULL, TIMESTAMP(@today - INTERVAL 5 DAY, '11:40:00'), TIMESTAMP(@today - INTERVAL 5 DAY, '11:40:00'));
SET @r103 = LAST_INSERT_ID();

-- 104 메타플로우웍스 · 2차 역량진단 · 참여기관 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_metaflow, @co_metaflow, @p_sd2, '제조 AI 검사 모델 담당할 참여기관 구합니다. 라벨링 인력 보유 기업 우대', CONCAT(
    '자동차 부품 표면 결함 검사 모델을 개발합니다. 이번 진단에서는 AI 모델 성능뿐 아니라 학습 데이터 관리 체계도 함께 보기 때문에, 라벨링 운영과 검수 기준을 맡아 주실 참여기관을 찾습니다.', '\n\n',
    '학습 데이터는 약 12만 장이고 그중 4만 장은 이미 1차 라벨링이 끝났습니다. 라벨링 가이드는 저희가 제공하고, 결함 등급 판정 기준은 함께 다듬었으면 합니다.', '\n\n',
    'GPU 학습 환경은 저희 쪽 서버를 쓰셔도 되고, 자체 환경이 있으시면 그쪽에 맞추겠습니다. 데이터 반출은 NDA 체결 후 지정 폴더 방식으로만 가능합니다.', '\n\n',
    '진단 대응 문서 작성은 저희가 전담하니 참여기관은 데이터 품질 쪽만 봐 주시면 됩니다.'
), 'LEAD', 'PARTICIPANT', 1, '대전', NULL, JSON_ARRAY('이미지 라벨링 인력 보유', '결함 검수 기준 수립', 'GPU 학습 환경 보유'),
@today + INTERVAL 12 DAY, NULL, TIMESTAMP(@today - INTERVAL 4 DAY, '10:10:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '10:10:00'));
SET @r104 = LAST_INSERT_ID();

-- 105 노바인스펙트 · 공급기업 역량진단(상시) · 주관기관 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_nova, @co_nova, @p_sdbase, '공정 자동화 설비 실적 있는 주관기관과 컨소시엄 구성하고 싶습니다', CONCAT(
    '검사 자동화 설비를 제작하는 회사입니다. 공급기업 역량진단을 받아 두면 이후 구축사업 참여에 도움이 된다고 들어 신청을 준비하고 있습니다.', '\n\n',
    '다만 저희는 설비 제작이 중심이라 시스템 쪽 실적이 부족합니다. 공정 자동화 설비 납품 실적과 유지보수 조직을 갖춘 주관기관과 함께하면 진단 항목을 더 넓게 채울 수 있을 것 같습니다.', '\n\n',
    '저희가 참여기관으로 들어가고, 남동공단 내 설비 제작 현장과 최근 3년 납품 실적 자료를 제공하겠습니다. 상시 접수 사업이라 일정은 여유 있게 잡을 수 있습니다.'
), 'PARTICIPANT', 'LEAD', 1, '인천', 5, JSON_ARRAY('공정 자동화 설비 납품 실적', '설비 유지보수 조직 보유'),
@today + INTERVAL 22 DAY, NULL, TIMESTAMP(@today - INTERVAL 3 DAY, '16:25:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '16:25:00'));
SET @r105 = LAST_INSERT_ID();

-- 106 리테일노드 · 스마트상점 · 수요처 3곳
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_retail, @co_retail, @p_store, '스마트상점 기술을 도입할 매장 운영사(수요처) 3곳을 찾습니다', CONCAT(
    '무인 매장 운영 솔루션을 만드는 회사이고 스마트상점 기술공급기업으로 등록돼 있습니다. 이번 모집에 함께 신청할 매장 운영사를 찾습니다.', '\n\n',
    '지원 유형은 구입형과 렌탈형 모두 가능하고, 저희 쪽 기술은 키오스크 연동 재고관리와 대기열 안내입니다. 배리어프리 옵션도 있어 해당 기술로 신청하면 한도가 더 큽니다.', '\n\n',
    '요청드리는 건 두 가지입니다. 첫째 최근 3개월 POS 데이터 열람 동의, 둘째 설치 후 2개월간 현장 실증 협조입니다. 매장 3곳 이상을 운영하시면 효과 측정이 수월해 우선 검토하겠습니다.', '\n\n',
    '신청 서류와 견적서는 저희가 작성해 드리고, 매장에서는 사업자등록증과 임대차계약서만 준비하시면 됩니다.'
), 'LEAD', 'DEMAND', 3, '전국', 1, JSON_ARRAY('오프라인 매장 3곳 이상 운영', 'POS 데이터 제공 가능', '현장 실증 협조 가능'),
@today + INTERVAL 14 DAY, NULL, TIMESTAMP(@today - INTERVAL 8 DAY, '13:00:00'), TIMESTAMP(@today - INTERVAL 8 DAY, '13:00:00'));
SET @r106 = LAST_INSERT_ID();

-- 107 에코미터솔루션 · 공공기관 실증(상시) · 주관기관 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_eco, @co_eco, @p_poc, '공공기관 실증 함께할 주관기관 구합니다 (에너지 진단 솔루션)', CONCAT(
    '건물 에너지 사용량을 계측해 낭비 구간을 찾아 주는 솔루션을 만듭니다. 성능인증은 받았지만 공공 납품 실적이 없어 실증지원 사업으로 레퍼런스를 만들려고 합니다.', '\n\n',
    '저희는 참여기관으로 들어가 계측기 설치와 분석 리포트를 맡습니다. 공공기관 납품 실적이 있고 실증 과제를 총괄해 보신 주관기관을 찾습니다. 실증 현장 섭외를 함께 해 주시면 가장 좋습니다.', '\n\n',
    '계측기는 저희 부담으로 제공하고, 실증 종료 후 회수하지 않고 현장에 남기는 조건도 가능합니다. 부산·경남 지역 공공기관이면 이동이 편하지만 수도권도 괜찮습니다.'
), 'PARTICIPANT', 'LEAD', 1, '부산', 5, JSON_ARRAY('공공기관 납품 실적', '실증 과제 총괄 경험', '조달 등록 제품 보유'),
@today + INTERVAL 20 DAY, NULL, TIMESTAMP(@today - INTERVAL 6 DAY, '09:00:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '09:00:00'));
SET @r107 = LAST_INSERT_ID();

-- 108 내 기업(그루브데이터) · 사업재편 · 주관기업 찾음
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_me, @co_me, @p_reorg, '사업재편 준비 중인 제조 주관기업 찾습니다. 데이터 분석은 저희가 맡습니다', CONCAT(
    '제조 설비 로그와 ERP 데이터를 묶어 원가·불량 지표를 만드는 데이터 분석 회사입니다. 이번 사업재편 과제에서 참여기업으로 들어가 성과지표 설계와 분석을 전담하려 합니다.', '\n\n',
    '찾는 곳은 업력 5년 이상이면서 실제 제조 현장을 가진 주관기업입니다. 사업재편 승인 경험이 있으면 좋지만 필수는 아니고, 이제 준비를 시작하신 곳도 환영합니다. 설비 로그가 3년 이상 쌓여 있으면 지표를 만들기 훨씬 수월합니다.', '\n\n',
    '분석 인력은 4명이고 최근 2년간 스마트공장 고도화 과제 3건에 참여기관으로 들어갔습니다. 계획서에서 저희가 맡는 분량은 성과지표 정의와 데이터 수집 계획 부분이고, 초안은 먼저 정리해 공유드릴 수 있습니다.', '\n\n',
    '참여기업 지분은 총 사업비의 20~25% 선을 생각하고 있으며 협의 가능합니다. 공고 접수 마감 전에 서로 확인을 끝냈으면 합니다.'
), 'PARTICIPANT', 'LEAD', 1, '전국', 5, JSON_ARRAY('제조 현장 보유', '사업재편 승인 경험', '설비 로그 3년 이상 보유'),
@today + INTERVAL 30 DAY, NULL, TIMESTAMP(@today - INTERVAL 16 DAY, '09:30:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '09:30:00'));
SET @r108 = LAST_INSERT_ID();

-- 109 팜브릿지랩 · 2차 역량진단 · 모집 마감일 경과 → CLOSED
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_farm, @co_farm, @p_sd2, '설비 데이터 수집 모듈 공동 개발할 참여기관 구합니다', CONCAT(
    '스마트팜 환경 데이터를 다루던 경험을 제조 쪽으로 넓히려 합니다. 설비 데이터 수집 모듈을 함께 개발할 참여기관을 찾았습니다.', '\n\n',
    '모집은 마감했고 참여기관은 확정됐습니다. 같은 주제로 다시 모집하게 되면 새 글을 올리겠습니다.'
), 'LEAD', 'PARTICIPANT', 1, '전북', NULL, JSON_ARRAY('PLC 통신 개발', '엣지 게이트웨이 양산 경험'),
@today - INTERVAL 5 DAY, NULL, TIMESTAMP(@today - INTERVAL 19 DAY, '10:00:00'), TIMESTAMP(@today - INTERVAL 19 DAY, '10:00:00'));
SET @r109 = LAST_INSERT_ID();

-- 110 메타플로우웍스 · AX 바우처 · 공고 접수 종료 → CLOSED
INSERT INTO partner_recruitment (account_id, company_id, support_program_id, title, body, own_role, seeking_role, seeking_count, region, minimum_company_age_years, capabilities, recruitment_deadline, closed_at, created_at, updated_at)
VALUES (@acc_metaflow, @co_metaflow, @p_ax, 'AX 원스톱 바우처 수요기업으로 함께할 제조기업 2곳 찾습니다', CONCAT(
    'AI 공급기업으로 컨소시엄에 들어갑니다. 바우처를 받아 AI를 도입할 수요기업 2곳을 찾습니다.', '\n\n',
    '저희 솔루션은 설비 이상 예지와 품질 예측이고, 수요기업에는 설비 데이터 반출 승인과 도입 의사결정 권한이 필요합니다. 데이터가 아직 정리돼 있지 않아도 수집 단계부터 같이 설계할 수 있습니다.', '\n\n',
    '접수 마감이 지나 이 모집은 종료되었습니다.'
), 'LEAD', 'DEMAND', 2, '전국', 3, JSON_ARRAY('제조 현장 보유', 'AI 도입 의사결정 가능', '데이터 반출 승인 가능'),
@today - INTERVAL 132 DAY, NULL, TIMESTAMP(@today - INTERVAL 153 DAY, '11:15:00'), TIMESTAMP(@today - INTERVAL 153 DAY, '11:15:00'));
SET @r110 = LAST_INSERT_ID();

-- ---------------------------------------------------------------------------------------------------------------------
-- 5. 제안 22건. 상태(대기·수락·거절·철회·만료)는 서버가 응답·철회·7일 응답 기한·모집 상태로 계산
-- ---------------------------------------------------------------------------------------------------------------------
-- 내 모집글(108)로 받은 제안: 대기 2, 수락 1, 거절 1, 7일 무응답 만료 1
INSERT INTO partner_proposal (recruitment_id, proposer_account_id, proposer_company_id, message, share_profile, decision, responded_at, withdrawn_at, created_at, updated_at) VALUES
(@r108, @acc_nova, @co_nova, '인천 남동공단에서 검사 자동화 설비를 제작합니다. 자사 공장이 있고 업력은 13년입니다. 사업재편 승인 이력은 없지만 검사 장비 중심에서 데이터 서비스로 넘어가려 준비 중이라 분석 파트너가 필요합니다. 설비 로그는 2022년부터 남아 있고 ERP는 자체 구축본입니다. 통화 가능한 시간 알려 주시면 연락드리겠습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 2 DAY, '14:20:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '14:20:00')),
(@r108, @acc_vision, @co_vision, '사업재편을 검토 중인 제조기업입니다. 아직 내부 의사결정이 끝나지 않아 프로필 공개는 잠시 미뤄 두었습니다. 검토해 주시면 연락 후 업종과 매출 구조를 바로 공유드리겠습니다.', FALSE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 1 DAY, '09:05:00'), TIMESTAMP(@today - INTERVAL 1 DAY, '09:05:00')),
(@r108, @acc_gaon, @co_gaon, '창원에서 정밀 절삭가공을 합니다. 내연기관 부품 비중을 낮추는 방향으로 사업재편을 준비하고 있고 컨설팅사와 1차 검토까지 마쳤습니다. 주관기업은 저희가 맡겠습니다. 설비 로그는 2021년부터 있고 에너지 사용량은 월 단위로 정리돼 있습니다. 계획서 일정에 맞춰 움직일 수 있습니다.', TRUE, 'ACCEPTED', TIMESTAMP(@today - INTERVAL 4 DAY, '11:00:00'), NULL, TIMESTAMP(@today - INTERVAL 5 DAY, '10:30:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '11:00:00')),
(@r108, @acc_blue, @co_blue, '수산 가공 라인에 AI 검사 기능을 붙이고 싶습니다. 사업재편보다는 R&D 쪽에 관심이 있는데 함께 검토해 볼 수 있을까요?', TRUE, 'DECLINED', TIMESTAMP(@today - INTERVAL 7 DAY, '11:00:00'), NULL, TIMESTAMP(@today - INTERVAL 8 DAY, '15:10:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '11:00:00')),
(@r108, @acc_farm, @co_farm, '스마트팜 설비 데이터가 3년치 쌓여 있습니다. 재배 데이터와 에너지 사용량을 함께 볼 수 있는 분석 파트너를 찾고 있어 제안드립니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 13 DAY, '09:00:00'), TIMESTAMP(@today - INTERVAL 13 DAY, '09:00:00'));

-- 내가 보낸 제안: 대기 1, 수락 1, 철회 1, 거절 1, 모집 마감으로 만료 1
INSERT INTO partner_proposal (recruitment_id, proposer_account_id, proposer_company_id, message, share_profile, decision, responded_at, withdrawn_at, created_at, updated_at) VALUES
(@r101, @acc_me, @co_me, '설비 로그 분석과 리포팅을 전담할 수 있습니다. OPC UA 연동 경험은 2건이라 많지는 않지만, 수집된 데이터로 지표를 설계하고 진단 자료를 만드는 일은 계속 해 왔습니다. 안산 현장 방문 일정에 맞춰 갈 수 있고, 역할 범위를 조율할 수 있다면 미팅 요청드립니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 3 DAY, '10:00:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '10:00:00')),
(@r107, @acc_me, @co_me, '공공기관 실증 과제를 두 번 총괄한 경험이 있습니다. 주관기관을 맡고 실증 데이터 분석까지 함께 하겠습니다. 실증 기관 섭외도 저희 쪽 네트워크로 도울 수 있습니다. 계측 데이터 형식만 미리 공유해 주시면 분석 설계를 먼저 잡아 두겠습니다.', TRUE, 'ACCEPTED', TIMESTAMP(@today - INTERVAL 5 DAY, '11:00:00'), NULL, TIMESTAMP(@today - INTERVAL 6 DAY, '16:40:00'), TIMESTAMP(@today - INTERVAL 5 DAY, '11:00:00')),
(@r104, @acc_me, @co_me, '라벨링 인력을 상시 6명 운영하고 있어 제안드렸습니다. 결함 등급 기준 정리도 함께 할 수 있습니다.', TRUE, NULL, NULL, TIMESTAMP(@today - INTERVAL 3 DAY, '11:00:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '11:00:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '11:00:00')),
(@r106, @acc_me, @co_me, '자사 쇼룸 1곳을 운영 중입니다. 매장 3곳 조건에는 못 미치지만 POS 데이터 제공 쪽으로 참여할 수 있을지 문의드립니다.', TRUE, 'DECLINED', TIMESTAMP(@today - INTERVAL 7 DAY, '18:20:00'), NULL, TIMESTAMP(@today - INTERVAL 7 DAY, '09:45:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '18:20:00')),
(@r109, @acc_me, @co_me, '엣지 게이트웨이 연동 경험이 있어 제안드립니다. 수집 주기 설계와 이상치 필터링을 맡을 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 14 DAY, '13:00:00'), TIMESTAMP(@today - INTERVAL 14 DAY, '13:00:00'));

-- 다른 기업끼리 주고받은 제안: 목록의 제안 수를 채웁니다
INSERT INTO partner_proposal (recruitment_id, proposer_account_id, proposer_company_id, message, share_profile, decision, responded_at, withdrawn_at, created_at, updated_at) VALUES
(@r101, @acc_vision, @co_vision, '검사 데이터 수집 체계를 자체 구축하면서 PLC 태그 정의서로 수집 항목을 정리한 경험이 있습니다. 엣지 게이트웨이 구성은 처음이지만 현장 인력이 있어 안산 방문 일정에 맞출 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 6 DAY, '15:30:00'), TIMESTAMP(@today - INTERVAL 6 DAY, '15:30:00')),
(@r101, @acc_nova, @co_nova, '검사 설비 제작 쪽이라 데이터 연동 실적은 적지만 설비 측 통신 사양을 잘 알고 있어 제안드립니다.', TRUE, 'DECLINED', TIMESTAMP(@today - INTERVAL 7 DAY, '11:00:00'), NULL, TIMESTAMP(@today - INTERVAL 8 DAY, '10:15:00'), TIMESTAMP(@today - INTERVAL 7 DAY, '11:00:00')),
(@r102, @acc_farm, @co_farm, '농업 설비 쪽이지만 에너지 사용량 데이터를 3년치 갖고 있고 공정 전환 계획을 함께 세워 볼 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 4 DAY, '17:10:00'), TIMESTAMP(@today - INTERVAL 4 DAY, '17:10:00')),
(@r102, @acc_blue, @co_blue, '수산 가공 공정의 냉동·건조 설비를 탄소저감 설비로 바꾸는 계획을 검토 중입니다. 업종은 다르지만 전환 논리는 설명드릴 수 있습니다.', FALSE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 2 DAY, '11:50:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '11:50:00')),
(@r103, @acc_eco, @co_eco, '수준확인 항목 정리는 직접 해 본 적이 없지만 제조 현장 컨설팅 경험이 있는 협력 인력과 함께 주관을 맡을 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 3 DAY, '09:40:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '09:40:00')),
(@r104, @acc_retail, @co_retail, '매장 영상 데이터 라벨링을 상시 운영해 온 팀이 있어 결함 라벨링도 맡을 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 2 DAY, '16:05:00'), TIMESTAMP(@today - INTERVAL 2 DAY, '16:05:00')),
(@r106, @acc_blue, @co_blue, '직영 판매장 2곳을 운영합니다. 3곳 조건에 못 미치지만 실증에는 협조할 수 있습니다.', TRUE, 'DECLINED', TIMESTAMP(@today - INTERVAL 5 DAY, '11:00:00'), NULL, TIMESTAMP(@today - INTERVAL 6 DAY, '14:00:00'), TIMESTAMP(@today - INTERVAL 5 DAY, '11:00:00')),
(@r107, @acc_nova, @co_nova, '공공기관 납품 실적은 없지만 조달 등록 제품을 보유하고 있어 주관기관 역할을 검토해 볼 수 있을지 문의드립니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 3 DAY, '10:50:00'), TIMESTAMP(@today - INTERVAL 3 DAY, '10:50:00')),
(@r109, @acc_faconnect, @co_faconnect, 'OPC UA·Modbus 연동 모듈을 이미 갖고 있어 수집 모듈 공동 개발에 바로 붙을 수 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 12 DAY, '09:10:00'), TIMESTAMP(@today - INTERVAL 12 DAY, '09:10:00')),
(@r109, @acc_metaflow, @co_metaflow, '수집 모듈에서 나온 데이터를 바로 검사 모델 학습에 쓸 수 있어 관심이 있습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 10 DAY, '15:45:00'), TIMESTAMP(@today - INTERVAL 10 DAY, '15:45:00')),
(@r110, @acc_gaon, @co_gaon, '절삭 설비 진동·전류 데이터를 갖고 있어 이상 예지 도입을 검토하고 싶습니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 150 DAY, '10:20:00'), TIMESTAMP(@today - INTERVAL 150 DAY, '10:20:00')),
(@r110, @acc_nova, @co_nova, '검사 설비 제조 현장에 품질 예측을 붙여 보고 싶습니다. 데이터 반출 승인은 가능합니다.', TRUE, NULL, NULL, NULL, TIMESTAMP(@today - INTERVAL 149 DAY, '13:35:00'), TIMESTAMP(@today - INTERVAL 149 DAY, '13:35:00'));

SELECT '개발용 목데이터 완료' AS result,
       (SELECT COUNT(*) FROM support_program WHERE source_code = 'DEMO') AS programs,
       (SELECT COUNT(*) FROM company WHERE account_id IN (@acc_me, @acc_faconnect, @acc_gaon, @acc_vision, @acc_metaflow, @acc_nova, @acc_retail, @acc_eco, @acc_farm, @acc_blue)) AS companies,
       (SELECT COUNT(*) FROM partner_recruitment WHERE id BETWEEN @r101 AND @r110) AS recruitments,
       (SELECT COUNT(*) FROM partner_proposal WHERE recruitment_id BETWEEN @r101 AND @r110) AS proposals;
