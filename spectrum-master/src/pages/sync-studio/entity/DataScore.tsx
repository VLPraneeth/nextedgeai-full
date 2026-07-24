import { useNavigate } from '@reach/router';
import Spin from 'antd/lib/spin';
import moment from 'moment';
import { useEffect, useMemo } from 'react';

import ContributingFactors from 'components/data-fitness/ContributingFactors';
import { ResponsiveSparkline as Sparkline, SparklineTitle } from 'components/data-fitness/Sparkline';
import { HorizontalGauge } from 'components/datavis/gauges';
import { useFieldsetContext } from 'components/Fieldset';
import { Stack } from 'components/layout';
import useRerenderAfterDelay from 'hooks/useRerenderAfterDelay';
import { HALF_SECOND } from 'store/api/constants';
import { useDataScoreForEntity } from 'store/datascore';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './DataScore.less';

const tn = tNamespaced('DataFitnessPanel');

interface DataScoreProps {
  entityId: string;
}

const yyyymmddToDate = (dateString: string) => moment(dateString, ['YYYY-MM-DD', 'YYYY/MM/DD']).valueOf();

const makeTrendString = (delta: number, numberOfDays: number) => {
  const deltaSign = delta > 0 ? '+' : delta < 0 ? '-' : '';
  return tn('delta_over_days', { count: numberOfDays, deltaSign, delta });
};

const DataScore = ({ entityId }: DataScoreProps) => {
  const navigate = useNavigate();

  // Delay fetching for a second in case the user is double clicking the entity
  // node to navigate to the pipeline. This will reduce extra api calls.
  const readyToFetch = useRerenderAfterDelay(HALF_SECOND);

  const { data, dataScoreStatus, isLoading } = useDataScoreForEntity(readyToFetch ? entityId : undefined);
  const fieldset = useFieldsetContext();

  useEffect(() => {
    if (fieldset && data) {
      fieldset.updateCollapsedBadge(Math.ceil(data.score).toString());
    }
  }, [fieldset, data]);

  const sparklineData = useMemo(() => {
    return Object.entries(data?.trend?.dataPoints || {})
      .map(([date, value]) => ({
        date: yyyymmddToDate(date),
        value,
      }))
      .sort((datumA, datumB) => datumA.date - datumB.date);
  }, [data?.trend?.dataPoints]);

  return (
    <div className="data-score-container">
      {isLoading || !readyToFetch ? (
        <Spin delay={200} spinning={isLoading} />
      ) : dataScoreStatus === 'available' ? (
        <Stack spacing="lg">
          {data && <HorizontalGauge subTitle={data.label} value={data.score} />}
          <Stack spacing="xxs">
            {data?.trend && (
              <Stack spacing="xxs">
                <SparklineTitle>{makeTrendString(data.trend.deltaPercent, data.trend.rangeInDays)}</SparklineTitle>
                <Sparkline data={sparklineData} />
              </Stack>
            )}
            <ContributingFactors
              entityId={entityId}
              factors={data?.factors}
              onRequestShowRecords={(factor, entityId) => {
                navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }, { factorId: factor.factorId }));
              }}
            />
          </Stack>
        </Stack>
      ) : (
        <div className="data-score-not-available">{tn('not_available')}</div>
      )}
    </div>
  );
};

export default DataScore;
