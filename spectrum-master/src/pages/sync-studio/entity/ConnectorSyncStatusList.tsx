//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { ChangeEvent, useEffect, useState } from 'react';

import { ListItem, TextTag } from 'components';
import InlineSvg from 'components/icons/InlineSvg';
import InputFilter from 'components/InputFilter';
import useUserLocalMoment from 'hooks/moment';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { ConnectorSyncStatusModel } from 'store/entity-pipeline/types';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { SHORT_DATE_TIME_TZ_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';

import { DirectionId } from '../types';

import './ConnectorSyncStatusList.less';

export interface ConnectorSyncStatusListProps {
  className?: string;
  filterPlaceholder?: string;
  syncStatuses?: ConnectorSyncStatusModel[];
  filterDisplayBreakpoint?: number;
  syncDirection: DirectionId;
}

const tn = tNamespaced('ConnectorSyncStatusList');

const ConnectorSyncStatusList = ({
  className,
  filterPlaceholder,
  syncStatuses,
  filterDisplayBreakpoint = 5,
  syncDirection,
}: ConnectorSyncStatusListProps) => {
  const [filterText, setFilterText] = useState('');
  return (
    <div className={cx('synri-connector-status-container', className)}>
      {(syncStatuses?.length || 0) >= filterDisplayBreakpoint && (
        <InputFilter
          className="synri-connector-status-filter"
          placeholder={filterPlaceholder}
          onChange={(evt: ChangeEvent<HTMLInputElement>) => setFilterText(evt.target.value)}
        />
      )}
      {syncStatuses && (
        <div className="synri-connector-statuses-list">
          {syncStatuses
            ?.filter((s) => !filterText || s.connectorName?.toLowerCase().indexOf(filterText?.toLowerCase()) >= 0)
            .map((syncStatus) => (
              <ConnectorSyncStatus key={syncStatus.entityId} syncStatus={syncStatus} syncDirection={syncDirection} />
            ))}
        </div>
      )}
    </div>
  );
};

export default ConnectorSyncStatusList;

interface ConnectorSyncStatusProps {
  syncStatus: ConnectorSyncStatusModel;
  syncDirection: DirectionId;
}

export const ConnectorSyncStatus = ({ syncStatus, syncDirection }: ConnectorSyncStatusProps) => {
  const moment = useUserLocalMoment();

  const [processUpTo, setProcessUpTo] = useState('');

  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();
  const showDraftTag = connectorIsCustomDraft(connectorIdToMetadataMap[syncStatus.connectorId]);

  useEffect(() => {
    syncStatus.processedUpTo &&
      setProcessUpTo(moment(syncStatus.processedUpTo).format(SHORT_DATE_TIME_TZ_DISPLAY_FORMAT));
  }, [moment, syncStatus]);

  return (
    <ListItem
      title={tn('title', { connectorName: syncStatus.connectorName, entityName: syncStatus.entityName })}
      titleTooltip={tn('title', { connectorName: syncStatus.connectorName, entityName: syncStatus.entityName })}
      description={
        processUpTo &&
        tn('last_changed_record_timestamp', {
          time: processUpTo,
          interpolation: { escapeValue: false },
        })
      }
      descriptionTooltip={
        processUpTo &&
        tn(
          syncDirection === DirectionId.SYNC_FROM ? 'last_changed_source_tooltip' : 'last_changed_destination_tooltip',
          {
            connectorName: syncStatus.connectorName,
            time: processUpTo,
            interpolation: { escapeValue: false },
          }
        )
      }
      rightContent={showDraftTag && <TextTag text="Draft" color="orange" />}
      icon={
        syncStatus.iconPath && (
          <InlineSvg
            className="synri-connector-statuses-icon"
            src={syncStatus.iconPath}
            title={syncStatus.connectorType}
          />
        )
      }
    />
  );
};
