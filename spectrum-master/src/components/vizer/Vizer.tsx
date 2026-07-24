import cx from 'classnames';
import { useMemo } from 'react';

import { ReactComponent as InfoSign } from 'assets/icons/info-icon-solid.svg';
import { DataCardError } from 'pages/insights-studio/components/data-card-error/DataCardError';
import DataCardErrorBoundary from 'pages/insights-studio/components/data-card/DataCardErrorBoundary';
import { compileVizerLabel } from 'pages/insights-studio/utils/UnifiedDataCard.util';
import { DataCardContent, VariableValue } from 'store/insights-studio/types';
import { tNamespaced } from 'utils/i18nUtil';

import { VizerGraphMap } from './utils/VizerGraphComponents';
import './Vizer.less';

const tn = tNamespaced('InsightsStudio.Vizer');

const LABEL_HEIGHT = 28;

export interface VizerProps {
  dataCardId?: string;
  dataCardContent: DataCardContent;
  graphHeight: number;
  dataConfiguration?: Record<string, VariableValue>;
}
/**
 * Vizer (short for Visualizer) provides a top-level component API for
 * rendering data visualizations in Syncari's Insights Studio.
 */
export const Vizer = ({ dataCardContent, graphHeight, dataConfiguration, dataCardId }: VizerProps) => {
  // Map graph type to component
  const GraphComponent = VizerGraphMap[dataCardContent.configuration?.vizType?.toLowerCase() ?? ''];

  const [label, height] = useMemo(() => {
    if (
      dataCardContent.configuration?.vizLabelVisible &&
      dataCardContent.configuration.vizLabel &&
      dataCardContent.configuration.variablesMap
    ) {
      const height = graphHeight - LABEL_HEIGHT;
      return [
        <div className="vizer__label">
          {compileVizerLabel(dataCardContent.configuration.vizLabel, dataConfiguration)}
        </div>,
        height,
      ];
    }
    return [null, graphHeight];
  }, [
    dataCardContent.configuration?.variablesMap,
    dataCardContent.configuration?.vizLabel,
    dataCardContent.configuration?.vizLabelVisible,
    dataConfiguration,
    graphHeight,
  ]);

  if (!GraphComponent) {
    return <DataCardError />;
  }

  if (!dataCardContent.data?.rows || dataCardContent.data?.rows?.length === 0) {
    return <DataCardError error={{ title: tn('no_data_title'), body: tn('no_data_body') }} icon={<InfoSign />} />;
  }

  return (
    <DataCardErrorBoundary>
      <div className={cx('vizer', dataCardContent.configuration?.vizLabelVisible && 'vizer--with-label')}>
        {dataCardContent.configuration?.vizLabelPosition === 'TOP' && label}
        <GraphComponent
          configuration={dataCardContent.configuration}
          data={dataCardContent.data}
          height={height}
          dataCardId={dataCardId}
        />
        {dataCardContent.configuration?.vizLabelPosition === 'BOTTOM' && label}
      </div>
    </DataCardErrorBoundary>
  );
};
