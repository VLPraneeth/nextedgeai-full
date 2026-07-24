import Button from 'components/Button';
import { HStack, Stack } from 'components/layout';
import { EnhancedDataScoreFactor } from 'store/datascore';
import { tNamespaced } from 'utils/i18nUtil';

import DataScoreBatteryMeter from './DataScoreBatteryMeter';

import './ContributingFactors.less';

const tn = tNamespaced('DataFitness.ContributingFactors');

export interface ContributingFactorProps {
  entityId: string;
  factor: EnhancedDataScoreFactor;
  onRequestShowRecords: (factor: EnhancedDataScoreFactor, entityId: string) => void;
}

export const ContributingFactor = ({ entityId, factor, onRequestShowRecords }: ContributingFactorProps) => {
  const { label, description, fieldName, averageScore } = factor;

  return (
    <div className="contributing-factor-container">
      <HStack align="start" spacing="sm">
        <DataScoreBatteryMeter className="contributing-factory-battery-meter" score={averageScore} />
        <div>
          <Stack spacing="z">
            <span className="contributing-factor-title">{label}</span>
            <div className="contributing-factor-description">{description}</div>
            <Button
              role="button"
              htmlType="button"
              type="link"
              className="contributing-factor-records-link"
              aria-label={tn('show_records_aria_label', { fieldName, entityId })}
              onClick={() => onRequestShowRecords(factor, entityId)}>
              {tn('show_records')}
            </Button>
          </Stack>
        </div>
      </HStack>
    </div>
  );
};

export interface ContributingFactorsProps {
  entityId: string;
  factors?: ContributingFactorProps['factor'][];
  onRequestShowRecords: ContributingFactorProps['onRequestShowRecords'];
}

const ContributingFactors = ({ entityId, factors, onRequestShowRecords }: ContributingFactorsProps) => {
  return (
    <div className="contributing-factors-list">
      <div className="contributing-factors-title">{tn('title')}</div>
      {factors && factors.length ? (
        <Stack spacing="xs">
          {factors.map((factor, idx) => (
            <ContributingFactor
              key={idx}
              entityId={entityId}
              onRequestShowRecords={onRequestShowRecords}
              factor={factor}
            />
          ))}
        </Stack>
      ) : (
        <div className="contributing-factors-empty-state">{tn('no_factors')}</div>
      )}
    </div>
  );
};

export default ContributingFactors;
