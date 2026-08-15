import { tNamespaced } from 'utils/i18nUtil';

import './MarketingFallbackContent.scss';

const tn = tNamespaced('MarketingFallbackContent');

const introUrl = '/';

export const MarketingFallbackContent = () => {
  return (
    <div className="marketing-fallback-content">
      <aside>
        <h1 className="marketing-fallback-content__header margin-bottom">{tn('header')}</h1>
        <h2 className="marketing-fallback-content__blurb--emphasis margin-bottom">{tn('sub_heading')}</h2>

        <p className="marketing-fallback-content__blurb">{tn('bullet_1')}</p>
        <p className="marketing-fallback-content__blurb">{tn('bullet_2')}</p>
        <p className="marketing-fallback-content__blurb">{tn('bullet_3')}</p>
        <p className="marketing-fallback-content__blurb">{tn('bullet_4')}</p>
        <p className="marketing-fallback-content__blurb margin-bottom">{tn('bullet_5')}</p>

        <p
          className="marketing-fallback-content__blurb"
          dangerouslySetInnerHTML={{ __html: tn('learn_more', { link: introUrl }) }}></p>
      </aside>
    </div>
  );
};
