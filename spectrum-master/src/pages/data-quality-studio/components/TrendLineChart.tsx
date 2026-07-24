import moment from 'moment';
import { useMemo } from 'react';

import { SHORT_DATE_FORMAT } from 'utils/DateUtil';
import { lazily } from 'utils/ModuleUtils';

import { LineChartComponent } from '../types';
import { getProps } from '../utils';

// lazy components
const { ResponsiveLineChart: LineChart } = lazily(() => import('components/datavis/charts/LineChart'));

export type TrendLineChartProps = { data: LineChartComponent['data'] } & ReturnType<typeof getProps>;

const TrendLineChart = ({ data, ...props }: TrendLineChartProps) => {
  // TODO: some of this config should be moved server-side so that we don't need it here and allow full control from be
  // also sort should come from the server
  const enhancedData = useMemo(
    () =>
      data.map((series) =>
        Array.isArray(series)
          ? series
              ?.slice()
              .map((datum) => ({ ...datum, x: moment(datum.x, SHORT_DATE_FORMAT).valueOf() }))
              .sort((a, b) => a.x - b.x)
          : []
      ),
    [data]
  );

  // TODO: maybe get this from the server?
  const timeDomainBounds = enhancedData?.[0]?.[0]
    ? [enhancedData[0][0].x, enhancedData[0][enhancedData[0].length - 1].x]
    : [];

  return (
    <LineChart
      xScale={{
        type: 'time',
        domain: timeDomainBounds,
      }}
      yScale={{
        type: 'linear',
        domain: [0, 100],
      }}
      data={enhancedData}
      {...props}
    />
  );
};

export default TrendLineChart;
