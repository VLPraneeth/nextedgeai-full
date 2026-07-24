import { Layout } from 'react-grid-layout';

import { LineChartDatum } from 'components/datavis/charts/LineChart';
import { PieChartDatum } from 'components/datavis/charts/PieChart/PieChart';
import { ArrayElement } from 'utils/TypeUtils';

import { DataScoreLineItemsProps } from './components/DataScoreLineItems';
import { TrendBadgeProps } from './components/TrendBadge';

export enum WidgetComponentType {
  DATASCORE_LINE_ITEMS = 'dataScoreLineItems',
  EMPTY_STATE = 'emptyState',
  GAUGE = 'gauge',
  HORIZONTAL_GAUGE = 'hgauge',
  HSTACK = 'hstack',
  LINE_CHART = 'lineChart',
  ENTITY_BREAKDOWN = 'entityBreakdown',
  PIE_CHART = 'pieChart',
  STACK = 'stack',
  TABLE = 'table',
  TREND_BADGE = 'trendBadge',
}

export interface Dashboard {
  id: string;
  name: string;
  title: string;
  entityId: string | null;
  entityApiName: string;
  widgets: WidgetMetadata[];
}

export interface SyncariLayout extends Omit<Layout, 'static'> {
  resizable: boolean;
}

export interface WidgetMetadata {
  id: string;
  name: string;
  title: string;
  layout: SyncariLayout;
  loadingText?: string;
  emptyStateIcon?: string;
  emptyStateText?: string;
}

// I'd like to make this take in component props as a generic, but I don't think
// it will work the way I want. I ran into roadblocks because
// the types don't really overlap (of course). Might investigate later, but it
// probably won't matter because it would porbably need to be casted later
type BasicConfigType = Record<string, any>;

interface WidgetComponentFactory<Component, Data extends any = any> {
  name: string | null;
  component: Component;

  config: BasicConfigType[];
  data: Data[];
}

interface WidgetLayoutComponentFactory<Component> extends WidgetComponentFactory<Component, never> {
  contents: WidgetComponent[];
}

type HStackComponent = WidgetLayoutComponentFactory<WidgetComponentType.HSTACK>;
type StackComponent = WidgetLayoutComponentFactory<WidgetComponentType.STACK>;

export type WidgetLayoutComponent = HStackComponent | StackComponent;
export type DataScoreLineItemsComponent = WidgetComponentFactory<
  WidgetComponentType.DATASCORE_LINE_ITEMS,
  ArrayElement<DataScoreLineItemsProps['scorecards']>
>;
export type EmptyStateComponent = WidgetComponentFactory<WidgetComponentType.EMPTY_STATE>;
export type GaugeComponent = WidgetComponentFactory<WidgetComponentType.GAUGE, { label: string; value: number }>;
export type HorizontalGaugeComponent = WidgetComponentFactory<
  WidgetComponentType.HORIZONTAL_GAUGE,
  { label: string; value: number }
>;
export type LineChartComponent = WidgetComponentFactory<WidgetComponentType.LINE_CHART, LineChartDatum[]>;
export type PieChartComponent = WidgetComponentFactory<WidgetComponentType.PIE_CHART, PieChartDatum>;
export type EntityBreakdownChartComponent = WidgetComponentFactory<WidgetComponentType.ENTITY_BREAKDOWN, PieChartDatum>;
export type TableComponent = WidgetComponentFactory<WidgetComponentType.TABLE, Record<string, any>>;
export type TrendBadgeComponent = WidgetComponentFactory<
  WidgetComponentType.TREND_BADGE,
  {
    trendDirection: TrendBadgeProps['trendDirection'];
    value: TrendBadgeProps['children'];
  }
>;

export type WidgetComponent =
  | WidgetLayoutComponent
  | DataScoreLineItemsComponent
  | EntityBreakdownChartComponent
  | EmptyStateComponent
  | GaugeComponent
  | HorizontalGaugeComponent
  | LineChartComponent
  | PieChartComponent
  | TableComponent
  | TrendBadgeComponent;

export type WidgetComponentProps<T = {}> = T & WidgetComponent['data'];

export interface Widget extends WidgetMetadata {
  // for now, only allow a stack as the first child
  contents: WidgetComponent[];
}

export const isLayoutComponent = (variableToCheck: any): variableToCheck is WidgetLayoutComponent => {
  return (
    typeof variableToCheck.component !== 'undefined' &&
    [WidgetComponentType.HSTACK, WidgetComponentType.STACK].includes(variableToCheck.component)
  );
};
