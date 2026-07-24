import Popover from 'antd/lib/popover';
import { useState } from 'react';

import ContributingFactors, { ContributingFactorsProps } from 'components/data-fitness/ContributingFactors';
import DataScoreBatteryMeter from 'components/data-fitness/DataScoreBatteryMeter';
import { HorizontalGauge } from 'components/datavis/gauges';
import I18nProvider from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { Predicate } from 'store/data-studio/types';
import { useDataScoreForEntity } from 'store/datascore';

import './DataScore.less';

export interface DataScoreProps {
  entityId: string;
  predicate?: Predicate;
  onRequestShowRecords: ContributingFactorsProps['onRequestShowRecords'];
}

const DataScore = ({ entityId, predicate, onRequestShowRecords }: DataScoreProps) => {
  const [isShowing, setIsShowing] = useState(false);
  const { data, isLoading } = useDataScoreForEntity(entityId, predicate);

  const dataScore = Math.ceil(data?.score || 0);

  return (
    <I18nProvider namespace="DataStudio.DataScore">
      <Popover
        placement="bottomLeft"
        trigger="hover"
        visible={!isLoading && isShowing}
        onVisibleChange={setIsShowing}
        className="data-studio-data-score-card-trigger"
        overlayClassName="data-studio-data-score-card"
        content={
          <div>
            {data ? (
              <Stack divider spacing="lg">
                <div className="data-score-card-header">
                  <div className="header-item">
                    <HorizontalGauge subTitle={data.label} value={data.score} />
                  </div>
                  {typeof data.sourceScore === 'number' && (
                    <HStack className="header-item data-score-source" align="baseline" justify="end" spacing="xs">
                      <span className="data-score-source-value">{data.sourceScore}</span>
                      <span className="data-score-source-label">
                        <TranslatedText text="at_source" />
                      </span>
                    </HStack>
                  )}
                </div>
                <ContributingFactors
                  entityId={entityId}
                  factors={data.factors}
                  onRequestShowRecords={onRequestShowRecords}
                />
              </Stack>
            ) : (
              <div className="data-score-card-header">
                <div className="data-score-source-label">
                  <TranslatedText text="not_available" />
                </div>
              </div>
            )}
          </div>
        }>
        <div>
          <HStack>
            <DataScoreBatteryMeter score={dataScore} size="large" />
            <TranslatedText color="gray-800" text="data_fitness_index" />
          </HStack>
        </div>
      </Popover>
    </I18nProvider>
  );
};

export default DataScore;
