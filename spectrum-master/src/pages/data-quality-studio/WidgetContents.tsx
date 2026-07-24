import { useMemo } from 'react';

import EmptyState from 'components/EmptyState';
import { tNamespaced } from 'utils/i18nUtil';
import { lazily, EnhancedReactLazy } from 'utils/ModuleUtils';
import { UnreachableCaseError } from 'utils/TypeUtils';

import { WidgetComponent, WidgetLayoutComponent, WidgetComponentType, isLayoutComponent } from './types';
import { getProps } from './utils';

// lazy components
const DataScoreLineItems = EnhancedReactLazy(() => import('./components/DataScoreLineItems'));
const { HStack } = lazily(() => import('components/layout'));
const HorizontalGauge = EnhancedReactLazy(() => import('components/datavis/gauges/HorizontalGauge'));
const { GaugeWithParentSize: Gauge } = lazily(() => import('components/datavis/gauges/Gauge'));
const EntityBreakdownChart = EnhancedReactLazy(() => import('./components/EntityBreakdownBarChart'));
const LineChart = EnhancedReactLazy(() => import('./components/TrendLineChart'));
const PieChart = EnhancedReactLazy(() => import('./components/EntityBreakdownPieChart'));
const { Stack } = lazily(() => import('components/layout'));
const Table = EnhancedReactLazy(() => import('./components/Table'));
const TrendBadge = EnhancedReactLazy(() => import('./components/TrendBadge'));

const tn = tNamespaced('DataQualityStudio.Root');

export const WidgetErrorState = () => {
  return (
    <EmptyState
      className="widget-error-state"
      icon="/assets/icons/error.svg"
      title={tn('widget_rendering_error_title')}
      description={tn('widget_rendering_error_description')}
    />
  );
};

export interface WidgetComponentProps {
  component: WidgetComponent | WidgetLayoutComponent;
}

const WidgetContent = ({ component }: WidgetComponentProps) => {
  return useMemo(() => {
    try {
      const props = getProps(component.config);

      if (isLayoutComponent(component)) {
        const children = getLayoutComponentContents(component);

        switch (component.component) {
          case WidgetComponentType.HSTACK:
            return <HStack {...props}>{children}</HStack>;

          case WidgetComponentType.STACK:
            return <Stack {...props}>{children}</Stack>;
        }
      }

      switch (component.component) {
        case WidgetComponentType.GAUGE: {
          const { label, value } = component.data?.[0];
          return <Gauge {...props} subTitle={label} value={value} />;
        }
        case WidgetComponentType.HORIZONTAL_GAUGE:
          const { label, value } = component.data?.[0];
          return <HorizontalGauge {...props} subTitle={label} value={value} />;

        case WidgetComponentType.ENTITY_BREAKDOWN:
          return <EntityBreakdownChart data={component.data} />;

        case WidgetComponentType.PIE_CHART:
          return <PieChart data={component.data} />;

        case WidgetComponentType.LINE_CHART:
          return <LineChart {...props} data={component.data} />;

        case WidgetComponentType.TREND_BADGE: {
          const { trendDirection, value } = component.data?.[0];
          return <TrendBadge trendDirection={trendDirection}>{value}</TrendBadge>;
        }
        case WidgetComponentType.TABLE: {
          const [config] = component.config;

          if (!config) {
            return null;
          }

          const { metadata, pageInfo } = config;
          // The item.rowId is passed from the backend in order to provide React
          // a stable ID for the row
          return (
            <Table
              data={component.data.map((item) => ({ id: item.rowId, ...item }))}
              metadata={metadata}
              pageInfo={pageInfo}
            />
          );
        }
        case WidgetComponentType.EMPTY_STATE: {
          const { title, description, icon } = component.data?.[0];
          return <EmptyState title={title} description={description} icon={icon} />;
        }
        case WidgetComponentType.DATASCORE_LINE_ITEMS: {
          return <DataScoreLineItems scorecards={component.data || []} />;
        }
      }

      if (process?.env?.NODE_ENV !== 'production') {
        console.error('Widget is unsupported', { component });
        throw new UnreachableCaseError(component);
      }

      return <EmptyState title={tn('missing_widget_title')} description={tn('missing_widget_description')} />;
    } catch (error) {
      return <WidgetErrorState />;
    }
  }, [component]);
};

const getLayoutComponentContents = (component: WidgetLayoutComponent) => {
  return component.contents.map((c, idx) => (
    <WidgetContent key={`${component.name || component.component}-${idx}`} component={c} />
  ));
};

export default WidgetContent;
