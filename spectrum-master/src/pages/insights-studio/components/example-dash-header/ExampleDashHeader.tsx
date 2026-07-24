import { ReactComponent as Icon } from 'assets/icons/data-trend.svg';
import { tNamespaced } from 'utils/i18nUtil';
import './ExampleDashHeader.less';

const tn = tNamespaced('InsightsStudio');

export const ExampleDashHeader = () => {
  return (
    <div className="example-dash-header">
      <div className="example-dash-header__icon">
        <Icon height={33} />
      </div>
      <div>
        <h2>{tn('example_header_title')}</h2>
        <p>{tn('example_header_body')}.</p>
      </div>
    </div>
  );
};
