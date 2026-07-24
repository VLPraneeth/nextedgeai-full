//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Spin } from 'antd';
import cx from 'classnames';
import moment from 'moment';
import * as React from 'react';
import { useEffect, useState } from 'react';
import { connect, ConnectedProps } from 'react-redux';
import { bindActionCreators } from 'redux';

import { getSyncStatus } from 'actions/entityPipelineActions';
import { ReactComponent as Warning } from 'assets/icons/warning.svg';
import { pipelineStatusColorMap, syncStatusColorMap } from 'components/graph/getTagDataForEntityNode';
import { HStack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import { TextTag } from 'components/text-tag';
import useRerenderAfterDelay from 'hooks/useRerenderAfterDelay';
import { RootState } from 'reducers/index';
import { HALF_SECOND } from 'store/api/constants';
import {
  selectDestinationSyncStatus,
  selectSourcesSyncStatus,
  selectSyncStatus,
} from 'store/entity-pipeline/selectors';
import {
  getFragmentShares,
  resetShareFragmentModal,
  shareFragment,
  showShareFragmentModal,
} from 'store/fragment/actions';
import AppConstants from 'utils/AppConstants';
import { SyncStatusType } from 'utils/AppConstants.types';
import { SHORT_DATE_TIME_DISPLAY_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';

import { DirectionId } from '../types';
import ConnectorSyncStatusList from './ConnectorSyncStatusList';

import './SyncStatus.less';

const tn = tNamespaced('SyncStatus');
const entityNodeNamespace = tNamespaced('EntityNode');
const { SYNC_STATUS, FETCH_STATUS } = AppConstants;

const connector = connect(
  (state: RootState) => ({
    getSyncStatusStatus: state.entityPipeline.getSyncStatusStatus,
    getSyncStatusErrorMessage: state.entityPipeline.getSyncStatusErrorMessage,
    // TODO: Add types
    sourcesSyncStatus: selectSourcesSyncStatus(state),
    destinationSyncStatus: selectDestinationSyncStatus(state),
    syncStatus: selectSyncStatus(state),
  }),
  (dispatch) => {
    return bindActionCreators(
      {
        showShareFragmentModal,
        shareFragment,
        getFragmentShares,
        resetShareFragmentModal,
        getSyncStatus,
      },
      dispatch
    );
  }
);

type SyncStatusProps = ConnectedProps<typeof connector> & {
  className?: string;
  entityId: string;
};

const SyncStatus = ({
  className,
  entityId,
  getSyncStatus,
  getSyncStatusStatus,
  syncStatus,
  sourcesSyncStatus,
  destinationSyncStatus,
}: SyncStatusProps) => {
  // Delay fetching for a second in case the user is double clicking the entity
  // node to navigate to the pipeline. This will reduce extra api calls.
  const readyToFetch = useRerenderAfterDelay(HALF_SECOND);

  useEffect(() => {
    readyToFetch && getSyncStatus(entityId);
  }, [readyToFetch, getSyncStatus, entityId]);

  const [syncStatusTime, setSyncStatusTime] = useState('');
  const [syncStatusText, setSyncStatusText] = useState('');

  useEffect(() => {
    if (syncStatus) {
      setSyncStatusTime(
        syncStatus.lastSyncTime
          ? tn('synced_from', {
              time: moment(syncStatus.lastSyncTime).format(SHORT_DATE_TIME_DISPLAY_FORMAT),
              interpolation: { escapeValue: false },
            })
          : entityNodeNamespace('never_synced')
      );
      // Override the sync status time to regular text only during testing
      syncStatus.status === SYNC_STATUS.TEST && setSyncStatusTime('');

      setSyncStatusText(
        syncStatus.status
          ? entityNodeNamespace(
              // Special case unpublished to show draft status.
              // Note: The sync status is undefined if the pipeline doesn't have draft
              syncStatus.status === AppConstants.SYNC_STATUS.UNPUBLISHED ? 'draft' : syncStatus.status
            )
          : ''
      );
    }
  }, [syncStatus]);

  return (
    <div className={cx('synri-sync-status', className)}>
      <Spin spinning={getSyncStatusStatus === FETCH_STATUS.LOADING || !readyToFetch}>
        {syncStatus ? (
          <>
            <div
              className={cx(
                'synri-sync-current-status',
                syncStatus?.status && `synri-sync-current-status-${syncStatus.status?.toLowerCase()}`
              )}>
              <TextTag
                color={
                  // See the setSyncStatusText comment above.
                  syncStatus.status === AppConstants.SYNC_STATUS.UNPUBLISHED
                    ? pipelineStatusColorMap[AppConstants.SYNCARI_NODE_STATUS.DRAFT]
                    : syncStatusColorMap[syncStatus.status as SyncStatusType]
                }
                text={syncStatusText}
                size="md"
              />
              <div className="synri-sync-status-time">{syncStatusTime}</div>
            </div>
            <div className="synri-sync-status-error-details">
              {syncStatus.errorDetails && <p>{syncStatus.errorDetails}</p>}
              {syncStatus.errorCount > 0 && (
                <div
                  // Note: i18next sanitize the token for script injection
                  dangerouslySetInnerHTML={{
                    __html: tn('error_last_cycle', { errorCount: syncStatus.errorCount }),
                  }}
                />
              )}
            </div>
            {syncStatus.warningCount ? (
              <HStack spacing="xs" className="synri-sync-status__warning">
                <Warning width={18} height={18} />
                <span>{tn('pipeline_warning', { count: syncStatus.warningCount })}</span>
              </HStack>
            ) : null}
            {sourcesSyncStatus && (
              <>
                <div className="synri-sync-status-sources">{tn('sources')}</div>
                <ConnectorSyncStatusList
                  syncStatuses={sourcesSyncStatus as any}
                  filterPlaceholder={tn('filter_sources')}
                  syncDirection={DirectionId.SYNC_FROM}
                />
              </>
            )}
            {destinationSyncStatus && (
              <>
                <div className="synri-sync-status-destination">{tn('destinations')}</div>
                <ConnectorSyncStatusList
                  syncStatuses={destinationSyncStatus as any}
                  filterPlaceholder={tn('filter_destinations')}
                  syncDirection={DirectionId.SYNC_TO}
                />
              </>
            )}
          </>
        ) : (
          <CenterLayout>{getSyncStatusStatus !== FETCH_STATUS.LOADING && tn('sync_status_not_available')}</CenterLayout>
        )}
      </Spin>
    </div>
  );
};

const ConnectedSyncStatus = connector(SyncStatus);
export { ConnectedSyncStatus as SyncStatus };
