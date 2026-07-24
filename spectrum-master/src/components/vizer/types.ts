import { Chart, Point, PointLabelObject } from 'highcharts';

import { DataCardData, DataCardVizConfig } from 'store/insights-studio/types';
import { KeysOf } from 'utils/TypeUtils';

import { displayFormatter } from './utils/VizerDisplayFormatter';

export interface VizerProps {
  configuration: DataCardVizConfig;
  data: DataCardData;
  height: number;
  dataCardId?: string;
  dashboardId?: string;
}

export type VizerComponent = (props: VizerProps) => JSX.Element;

export type VizerDisplayFormat = KeysOf<typeof displayFormatter>;

export interface FunnelPoint extends Point {
  percentageValue?: number;
  options: Point['options'] & {
    customPercentage?: string;
  };
}

export interface FunnelPointLabelObject extends PointLabelObject {
  point: PointLabelObject['point'] & {
    percentageValue?: number;
  };
}

export interface FunnelChart extends Chart {
  renderedLabels?: Highcharts.SVGElement[];
}
