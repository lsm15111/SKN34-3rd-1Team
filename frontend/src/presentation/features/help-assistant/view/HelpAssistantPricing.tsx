import { Link } from 'react-router'

import { pricingPlans } from '../../pricing/viewmodel/pricingPlans'
import { appPaths, publicPaths } from '../../../shared/routes/appPaths'
import { helpAssistantStyles as s } from './HelpAssistant.styles'

/**
 * 도우미 안에서 보여 주는 요금제 소개입니다. 요금제 화면과 같은 `pricingPlans`를 읽으므로
 * 두 곳의 이름·가격·기능이 어긋나지 않습니다. 좁은 패널이라 카드를 세로로 쌓습니다.
 */
export function HelpAssistantPricing({ inApp }: { inApp: boolean }) {
  return (
    <div className={s.planList}>
      {pricingPlans.map((plan) => (
        <article
          className={plan.isFeatured ? `${s.planCard} ${s.planCardFeatured}` : s.planCard}
          aria-labelledby={`assistant-plan-${plan.id}`}
          key={plan.id}
        >
          <div className={s.planTop}>
            <span className={s.planLabel}>{plan.label}</span>
            <span className={plan.isFeatured ? `${s.planStatus} ${s.planStatusFeatured}` : s.planStatus}>
              {plan.status}
            </span>
          </div>
          <h3 className={s.planName} id={`assistant-plan-${plan.id}`}>{plan.name}</h3>
          <p className={s.planPrice}>{plan.price}</p>
          <p className={s.planNote}>{plan.priceNote}</p>
          <ul className={s.planFeatures}>
            {plan.features.map((feature) => (
              <li className={s.planFeature} key={feature}>
                <span aria-hidden="true" className={s.planFeatureMark}>✓</span>
                {feature}
              </li>
            ))}
          </ul>
        </article>
      ))}

      <div className={s.menu}>
        <Link className={s.menuLink} to={inApp ? appPaths.pricing : publicPaths.pricing}>
          요금제 자세히 보기
        </Link>
      </div>
    </div>
  )
}
