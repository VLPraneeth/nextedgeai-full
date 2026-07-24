import Button from 'components/Button';
import { tc } from 'utils/i18nUtil';

import { ConfigurationContent } from './ConfigurationContent';

import './ConfigurationStep.scss';
export interface DatasetConfigurationStepProps {
  onCancel: () => void;
  onSuccess: () => void;
  onPrevious: () => void;
}

export const DatasetConfigurationStep = ({ onCancel, onPrevious, onSuccess }: DatasetConfigurationStepProps) => {
  return (
    <div className="dataset-configuration-step">
      <ConfigurationContent />
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('cancel')}</Button>
        <Button onClick={onPrevious}>{tc('previous')}</Button>
        <Button type="primary" htmlType="submit" form="data-card-form" onClick={onSuccess}>
          {tc('next')}
        </Button>
      </div>
    </div>
  );
};
