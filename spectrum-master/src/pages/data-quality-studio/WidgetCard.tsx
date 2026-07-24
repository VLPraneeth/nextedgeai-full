import cx from 'classnames';
import { Suspense } from 'react';

import { useWidget } from 'store/new-dashboard/hooks';

import { WidgetMetadata } from './types';
import WidgetContent, { WidgetErrorState } from './WidgetContents';
import WidgetContentSpinner from './WidgetContentSpinner';
import WidgetErrorBoundary from './WidgetErrorBoundary';

import './WidgetCard.less';

export interface WidgetCardProps extends Omit<WidgetMetadata, 'id' | 'layout'> {
  dashboardName: string;
}

const WidgetCard = ({ dashboardName, name, title, loadingText }: WidgetCardProps) => {
  const { loading, widget } = useWidget({
    dashboardName,
    widgetName: name,
  });

  const spinner = <WidgetContentSpinner tip={loadingText || widget?.loadingText} />;

  return (
    <div className="widget-card">
      <div className="widget-card-title">{title}</div>
      <div className={cx('widget-card-content')}>
        <WidgetErrorBoundary>
          <Suspense fallback={spinner}>
            {loading ? (
              spinner
            ) : !widget ? (
              <WidgetErrorState />
            ) : (
              widget.contents?.map((wc, idx) => <WidgetContent key={wc.name || idx} component={wc} />)
            )}
          </Suspense>
        </WidgetErrorBoundary>
      </div>
    </div>
  );
};

export default WidgetCard;
