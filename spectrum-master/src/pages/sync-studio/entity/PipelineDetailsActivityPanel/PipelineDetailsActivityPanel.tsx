import { useMatch } from '@reach/router';
import { Button } from 'antd';

import DrawerPanel from 'components/DrawerPanel';
import EntityDetails from 'components/EntityDetails';
import { useEnhancedSelector } from 'hooks/redux';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { routeToMatch } from 'utils/StringUtil';

import { PipelineDetailsPanelNames } from '../PipelineDetails/PipelineDetails.constants';
import { usePipelineDetailsSearchParameters } from '../PipelineDetails/PipelineDetails.hooks';

import './PipelineDetailsActivityPanel.scss';

const tn = tNamespaced('PipelineDetailsActivityPanel');
const tc = tNamespaced('Common');

export const PipelineDetailsActivityPanel = () => {
  const { values: searchParamValues, updateSearchParams } = usePipelineDetailsSearchParameters();
  const entities = useEnhancedSelector((state) => state.entity.entities);
  const match = useMatch(routeToMatch(RouteConstants.ENTITY));

  const visible = searchParamValues.panel === PipelineDetailsPanelNames.ACTIVITY;
  const entity = entities?.find((entity) => entity.id === match?.entityId);

  const handleClose = () => {
    const params = [{ name: 'panel', value: '' }];

    updateSearchParams(params);
  };

  if (!entity) {
    return null;
  }

  return (
    <DrawerPanel
      className="pipeline-details-activity-panel"
      onClose={handleClose}
      footer={
        <Button type="primary" onClick={handleClose}>
          {tc('close')}
        </Button>
      }
      title={tn('title', { entityName: entity.displayName })}
      visible={visible}
      width={1000}>
      <EntityDetails entityId={entity.id} entityName={entity.displayName} visible={visible} />
    </DrawerPanel>
  );
};
