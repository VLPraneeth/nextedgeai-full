import { ReactComponent as DataCardIcon } from 'assets/icons/dashboard.svg';
import Button from 'components/Button';
import { Text } from 'components/typography';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { EmptyPanelContent } from '../empty-panel-content/EmptyPanelContent';

import './RequirementNotMet.scss';

const tn = tNamespaced('InsightsStudio');

export interface RequirementNotMetProps {
  onClose: () => void;
}

export const RequirementNotMet = ({ onClose }: RequirementNotMetProps) => {
  return (
    <div className="requirements-not-net">
      <EmptyPanelContent icon={<DataCardIcon width={48} height={48} />}>
        <Text size="lg">{tn('no_published_pipeline')}</Text>
      </EmptyPanelContent>
      <div className="synri-drawer-panel__footer">
        <Button type="primary" htmlType="submit" form="data-card-form" onClick={onClose}>
          {tc('close')}
        </Button>
      </div>
    </div>
  );
};
