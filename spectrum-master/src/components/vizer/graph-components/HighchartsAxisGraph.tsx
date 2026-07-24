import Highcharts from 'highcharts';
import HighchartsReact from 'highcharts-react-official';

import { VizerProps } from '../types';
import { useHighchartsAxisGraph } from '../utils/useHighchartsAxisGraph';
import { VizerGraphColors } from '../utils/VizerGraphColors';

export const HighchartsAxisGraph = ({ configuration, data, height }: VizerProps) => {
  const { categories, yAxis, highchartSeries, tooltip } = useHighchartsAxisGraph({ configuration, data });

  // Highcharts docs: https://api.highcharts.com/highcharts/
  const graphOptions: Highcharts.Options = {
    // Color theme to use. Defaults to purple. Overridden by series-specific color values
    // Add to configuration in future.
    colors: configuration.colorTheme ? VizerGraphColors.legacyColorTheme(configuration.colorTheme) : undefined,
    chart: {
      height,
      spacingBottom: 0,
      spacingLeft: 0,
      spacingRight: 0,
      spacingTop: 14,
      type: configuration.vizType,
    },
    legend: { verticalAlign: configuration.legendPosition === 'TOP' ? 'top' : 'bottom' },
    credits: { enabled: false },
    title: {
      // Explicitly set to undefined to prevent display of
      // any title. Title is handled by DataCard.tsx
      text: undefined,
    },
    xAxis: { categories, title: { text: configuration.xaxis.displayName } },
    tooltip,
    series: highchartSeries,
    yAxis,

    plotOptions: {
      series: {
        stacking: configuration.stacking,
      },
    },
  };

  return (
    <div>
      <HighchartsReact highcharts={Highcharts} options={graphOptions} />
    </div>
  );
};
