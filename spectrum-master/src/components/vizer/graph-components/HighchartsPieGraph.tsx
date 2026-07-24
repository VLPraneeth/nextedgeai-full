import Highcharts, { Point } from 'highcharts';
import HighchartsReact from 'highcharts-react-official';

import { VizerProps } from '../types';
import { usePieVizer } from '../utils/usePieVizer';
import { formatValue } from '../utils/VizerDisplayFormatter';

export const HighchartsPieGraph = ({ configuration, data, height }: VizerProps) => {
  const { highchartSeries, tooltip, events } = usePieVizer({ configuration, data });

  // Highcharts docs: https://api.highcharts.com/highcharts/
  const graphOptions: Highcharts.Options = {
    chart: {
      height,
      spacingBottom: 10,
      spacingLeft: 0,
      spacingRight: 0,
      spacingTop: 5,
      type: configuration.vizType,
      events,
    },
    credits: { enabled: false },
    title: {
      // Explicitly set to undefined to prevent display of
      // any title. Title is handled by DataCard.tsx
      text: undefined,
    },
    tooltip,
    series: highchartSeries,
    plotOptions: {
      pie: {
        dataLabels: {
          enabled: configuration.labelVisible ?? true,
        },
        showInLegend: configuration.legendVisible ?? false,
      },
    },
    legend: {
      align: configuration.legendPosition === 'RIGHT' ? 'right' : 'left',
      verticalAlign: 'bottom',
      layout: 'vertical',
      itemMarginBottom: 8,
      labelFormatter() {
        const point = this as Point;
        const displayFormat = configuration.value?.displayFormat;
        const value = point.y && formatValue(displayFormat, point.y, point?.percentage);
        return `${point.name}<br/>${value}`;
      },
    },
  };

  return (
    <div>
      <HighchartsReact highcharts={Highcharts} options={graphOptions} />
    </div>
  );
};
