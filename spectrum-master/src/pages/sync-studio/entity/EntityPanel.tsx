//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import Fieldset from 'components/Fieldset';
import PropertyPanelAction, { PropertyPanelActionModel } from 'components/PropertyPanelAction';
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { tNamespaced } from 'utils/i18nUtil';

import DataScore from './DataScore';
import EntityTagsInput from './EntityTagsInput';
import { SyncStatus } from './SyncStatus';

import './EntityPanel.less';

const tn = tNamespaced('EntityEditorEntityPanel');
export interface EntityPanelProps {
  actions: PropertyPanelActionModel[];
  entityId: string;
  entityName: string;
  onClose: () => void;
}

const EntityPanel = ({ entityId, entityName, actions, onClose }: EntityPanelProps) => {
  return (
    <div className="syrni-entity-panel">
      <PropertyPanelTitle title={entityName} onClose={onClose} className="syrni-entity-panel__title" />
      {actions && <PropertyPanelAction actions={actions} collapsible />}
      <ScrollableArea>
        <Fieldset collapsible title={tn('sync_status')}>
          <SyncStatus entityId={entityId} />
        </Fieldset>
        <Fieldset title={tn('data_fitness_index')} collapsible>
          <DataScore entityId={entityId} />
        </Fieldset>
        <Fieldset collapsible title={tn('tags')}>
          <EntityTagsInput entityId={entityId} />
        </Fieldset>
      </ScrollableArea>
    </div>
  );
};

export default EntityPanel;
