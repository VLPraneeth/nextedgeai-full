import Highcharts from 'highcharts';
import HighchartsReact from 'highcharts-react-official';
import funnel from 'highcharts/modules/funnel';

import { FunnelPoint, FunnelPointLabelObject, VizerProps } from '../types';
import { useFunnelVizer } from '../utils/useFunnelVizer';
import { formatValue } from '../utils/VizerDisplayFormatter';

funnel(Highcharts);

export const HighchartsFunnelGraph = ({ configuration, data, height }: VizerProps) => {
  const { series, tooltip, colors, events } = useFunnelVizer({ configuration, data });

  const showLabelInside = configuration.labelPosition === 'INSIDE';
  const showLabel = configuration.displayAdditional === 'step_to_step_ratio' || configuration.labelVisible;
  // Highcharts docs: https://api.highcharts.com/highcharts/
  const graphOptions: Highcharts.Options = {
    chart: {
      height,
      spacingBottom: 20,
      spacingLeft: 0,
      spacingRight: 0,
      spacingTop: 5,
      type: configuration.vizType?.toLowerCase(),
      events,
    },
    colors,
    credits: { enabled: false },
    title: {
      // Explicitly set to undefined to prevent display of
      // any title. Title is handled by DataCard.tsx
      text: undefined,
    },
    tooltip,
    series,
    plotOptions: {
      funnel: {
        dataLabels: {
          enabled: showLabel ?? false,
          /* @ts-ignore - https://api.highcharts.com/highcharts/series.funnel.dataLabels.inside */
          inside: showLabelInside,
          formatter() {
            const pointLabel = this as FunnelPointLabelObject;
            const value =
              pointLabel.y &&
              formatValue(configuration.measure?.displayFormat, pointLabel.y, pointLabel?.point?.percentageValue);
            return `<div style="text-align: ${showLabelInside ? 'center' : 'left'}"><span>${
              this.point.name
            }</span><br/><span style="font-weight: normal">${value || ''}</span></div>`;
          },
          style: {
            textOverflow: 'ellipsis',
          },
          useHTML: showLabelInside,
        },
        showInLegend:
          (configuration.displayAdditional !== 'step_to_step_ratio' && configuration.legendVisible) ?? false,
        neckHeight: '0%',
        neckWidth: '25%',
        width: '65%',
        center: showLabel && configuration.labelPosition !== 'INSIDE' ? ['35%', '50%'] : ['50%', '50%'],
        reversed: configuration.sortBy === 'value' ? configuration.ascending : false,
        borderWidth: 0,
      },
    },
    legend: {
      align: configuration.legendPosition === 'RIGHT' ? 'right' : 'left',
      verticalAlign: 'bottom',
      layout: 'vertical',
      itemMarginBottom: 8,
      labelFormatter() {
        const point = this as FunnelPoint;
        const value = point.y && formatValue(configuration.measure?.displayFormat, point.y, point?.percentageValue);
        return `${point.name}<br/><span style="font-weight: normal">${value || ''}</span>`;
      },
      useHTML: true,
    },
  };

  return (
    <div>
      <HighchartsReact highcharts={Highcharts} options={graphOptions} />
    </div>
  );
};
