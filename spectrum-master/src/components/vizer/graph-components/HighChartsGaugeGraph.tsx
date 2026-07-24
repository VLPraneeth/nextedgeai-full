import Highcharts from 'highcharts';
import HighchartsReact from 'highcharts-react-official';
import HighchartsMore from 'highcharts/highcharts-more';

import { VizerProps } from '../types';
import { useGaugeVizer } from '../utils/useGaugeVizer';

HighchartsMore(Highcharts);

export const HighchartsGaugeGraph = ({ configuration, data, height }: VizerProps) => {
  const { series, yAxis } = useGaugeVizer({ configuration, data });

  // Highcharts docs: https://api.highcharts.com/highcharts/
  const graphOptions: Highcharts.Options = {
    chart: {
      height,
      spacingBottom: 5,
      spacingLeft: 5,
      spacingRight: 5,
      spacingTop: 0,
      type: configuration.vizType,
    },
    credits: { enabled: false },
    title: {
      // Explicitly set to undefined to prevent display of
      // any title. Title is handled by DataCard.tsx
      text: undefined,
    },
    pane: {
      startAngle: -90,
      endAngle: 89.9,
      background: undefined,
      center: ['50%', height < 100 ? '50%' : '75%'],
      size: '100%',
    },
    tooltip: {
      enabled: false,
    },
    series,
    yAxis,
  };

  return (
    <div>
      <HighchartsReact highcharts={Highcharts} options={graphOptions} />
    </div>
  );
};
