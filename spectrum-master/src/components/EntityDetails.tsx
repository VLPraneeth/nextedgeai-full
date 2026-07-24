import { Link } from '@reach/router';
import { Tooltip, Icon as AIcon } from 'antd';
import cx from 'classnames';
import { keyBy, map } from 'lodash';
import { useCallback, useEffect, useState } from 'react';

import { getSyncStatus } from 'actions/entityPipelineActions';
import { ReactComponent as Warning } from 'assets/icons/warning.svg';
import { ReactComponent as RefreshIcon } from 'assets/images/refresh-icon.svg';
import Icon from 'components/icons/Icon';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { getErrorText } from 'pages/sync-studio/pipeline-error/PipelineError.hooks';
import { selectAllConnectors, selectConnectorsMetadata } from 'store/connectors';
import { useLazyGetEntitySyncMetricsQuery } from 'store/pipeline-visibility/api';
import { EntityMetricsPayload } from 'store/pipeline-visibility/types';
import { format as formatDate, SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { t, tc, tCommon } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';
import RouteConstants from 'utils/RouteConstants';
import { entityIdIsValid } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import Button from './Button';
import { SYNCARI_ICON } from './icons/Icons';
import { HStack, Spacer } from './layout';
import { ScrollableArea } from './scrollable-area/ScrollableArea';
import { TextTag } from './text-tag';
import TreeSkeleton from './tree-skeleton';
import { Text, TranslatedText } from './typography';

import './EntityDetails.scss';

interface SyncDetailsRefreshProps {
  isRefreshing?: boolean;
  onRefresh?: () => void;
}

const SyncDetailsRefresh = ({ isRefreshing, onRefresh }: SyncDetailsRefreshProps) => {
  const [lastRefreshed, setLastRefreshed] = useState<Date | undefined>();
  return (
    <HStack justify="end" className="sync-details-refresh">
      <Tooltip
        title={
          lastRefreshed &&
          t('PipelineDetailsActivityPanel.last_synced', {
            datetime: formatDate(lastRefreshed, SHORT_DATE_TIME_FORMAT),
            interpolation: { escapeValue: false },
          })
        }
        placement="left">
        <Button
          className={cx('sync-details-refresh__refresh-button')}
          disabled={isRefreshing}
          onClick={() => {
            setLastRefreshed(new Date());
            onRefresh?.();
          }}>
          <AIcon component={wrapIcon(RefreshIcon)} />
          {tc('refresh')}
        </Button>
      </Tooltip>
    </HStack>
  );
};

const PipelineDetailsRow = (props: {
  durationUnit: string;
  lastProcessed: string | null;
  recordCountSuffix: string;
  title: string;
  duration: number | null;
  totalProcessedRecordsCount: number | null;
  notStarted: boolean;
}) => {
  const utcToLocal = useUtcTimeInUsersTimezone();

  return (
    <div className="pipeline-details-timeline-row">
      <Text weight="bold" color={props.notStarted ? 'gray-700' : 'gray-900'} size="md">
        {props.title}
      </Text>
      <div className="pipeline-details-timeline-row-details">
        {props.notStarted ? (
          <TranslatedText text="upcoming" weight="medium" color="gray-700" size="md" />
        ) : (
          <>
            <Text className="synri-pipeline-details-last-processed" weight="medium" color="gray-900" size="md">
              {utcToLocal(props.lastProcessed)}
            </Text>
            <Text weight="medium" color="gray-900" size="md">
              {props.totalProcessedRecordsCount
                ? props.totalProcessedRecordsCount + ' ' + props.recordCountSuffix
                : '-'}
            </Text>
            <Text className="synri-pipeline-details-duration" weight="medium" color="gray-900" size="md">
              {props.duration + ' ' + props.durationUnit?.toLowerCase()}
            </Text>
          </>
        )}
      </div>
    </div>
  );
};

interface EntityDetailsContentProps {
  refetch?: () => void;
  iconMap?: Record<string, string | null>;
  hasError?: boolean;
  entityId?: string;
  // TODO: It would be ideal to use the entityName from the metrics response but
  // if the entity is unmapped the metrics endpoint throws an error. So we're
  // passing the entityName directly.
  entityName?: string;
  metrics?: EntityMetricsPayload;
  loading?: boolean;
  fetchDetails?: () => void;
}

export const EntityDetailsContent = ({
  entityId,
  metrics,
  loading,
  hasError,
  iconMap,
  fetchDetails,
}: EntityDetailsContentProps) => {
  const utcToLocal = useUtcTimeInUsersTimezone();

  if (!(entityId && entityIdIsValid(entityId))) {
    return (
      <div className="synri-entity-details-emtpty-container">
        <TranslatedText text="select_an_entity" />
      </div>
    );
  }

  let content = null;

  if (loading) {
    content = (
      <div className="synri-entity-details-emtpty-container">
        <TranslatedText text="loading_details" />
      </div>
    );
  } else if (!metrics || hasError || (!metrics?.emptyLastSync && !metrics?.allStages)) {
    content = (
      <div className="synri-entity-details-emtpty-container">
        <TranslatedText text="no_sync_details" />
      </div>
    );
  } else {
    const items = metrics?.allStages?.map((stage) => {
      return {
        key: stage.title,
        label: <PipelineDetailsRow {...stage} notStarted={stage.status === 'NOT_STARTED'} />,
        children: (
          <div>
            {stage.details ? (
              map(stage.details, (detail) => {
                let iconUri = SYNCARI_ICON;
                let connectorName = tCommon('syncari');

                // detail.connectorId will have square brackets and
                // comma-separated values when there are potentially multiple
                // synapes (like for FPs). This value won't match a connector id
                // so we'll default to using the Syncari name and logo.
                const customUri = iconMap?.[detail.connectorId];
                if (customUri) {
                  iconUri = customUri;
                  connectorName = detail.connectorName;
                }

                return (
                  <div key={detail.connectorId} className="pipeline-details-timeline-row-icon-wrapper">
                    <div className="pipeline-details-synapse-icon">
                      <Icon
                        className={cx(iconUri === SYNCARI_ICON && 'synri-scale-syncari-icon')}
                        src={iconUri}
                        alt={connectorName}
                      />
                    </div>
                    <div className="pipeline-details-timeline-row">
                      <Text weight="bold" color="gray-900" size="md">
                        {connectorName}
                      </Text>
                      <div className="pipeline-details-timeline-row-details">
                        <Text
                          className="synri-pipeline-details-last-processed-with-icon"
                          weight="medium"
                          color="gray-900"
                          size="md">
                          {utcToLocal(detail.lastProcessed)}
                        </Text>
                        <Text weight="medium" color="gray-900" size="md">
                          {detail.totalProcessedRecordsCount === null
                            ? '-'
                            : `${detail.totalProcessedRecordsCount} ${stage.recordCountSuffix}`}
                        </Text>
                        <Text className="synri-pipeline-details-duration" weight="medium" color="gray-900" size="md">
                          {detail.duration + ' ' + detail.durationUnit?.toLowerCase()}
                        </Text>
                      </div>
                    </div>
                  </div>
                );
              })
            ) : (
              <TranslatedText text="stage_not_started" weight="medium" color="gray-700" size="md" />
            )}
          </div>
        ),
      };
    });

    const hasStages = Boolean(metrics?.allStages?.length);

    content = (
      <ScrollableArea className="pipeline-details-timeline">
        {metrics.emptyLastSync && (
          <div className="pipeline-details-subheader bottom-border">
            <div className="pipeline-details-subheader-message-container">
              <Text className="synri-pipeline-details-last-processed" weight="bold" color="gray-850" size="md">
                {tc('synced_time', { time: utcToLocal(metrics.lastSyncTime), interpolation: { escapeValue: false } })}
              </Text>

              <TranslatedText
                text="no_new_records"
                className="synri-pipeline-details-last-processed"
                color="gray-800"
                size="md"
              />
            </div>
          </div>
        )}
        {hasStages && (
          <div className="pipeline-details-subheader">
            <div className="pipeline-details-subheader-message-container">
              <Text className="synri-pipeline-details-last-processed" weight="bold" color="gray-850" size="md">
                {tc(metrics.errorCount ? 'unable_to_sync' : 'synced_time', {
                  time: utcToLocal(metrics.lastProcessed),
                  interpolation: { escapeValue: false },
                })}
              </Text>
              {metrics.errorCount || metrics.warningCount ? (
                <HStack spacing="xxs" className="pipeline-details-subheader-message-container__error-warning">
                  {metrics.errorCount && <TextTag text={tc('error')} color="red" />}
                  {metrics.warningCount && <Warning width={18} height={18} />}
                  <HStack spacing="sm">
                    <Text color="gray-900" size="md">
                      {getErrorText({ errorCount: metrics.errorCount, warningCount: metrics.warningCount })}
                    </Text>
                    <Link
                      to={makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
                        entityId,
                        graphVersion: 'approved',
                      })}>
                      {t('PipelineErrorState.review_pipeline')}
                    </Link>
                  </HStack>
                </HStack>
              ) : null}
              {metrics.emptyLastSync && (
                <TranslatedText
                  text="most_recent_with_changes"
                  className="synri-pipeline-details-last-processed"
                  color="gray-800"
                  size="md"
                />
              )}
            </div>
            <Text className="synri-pipeline-details-duration" weight="medium" color="gray-900" size="md">
              {metrics.duration + ' ' + metrics.durationUnit?.toLowerCase()}
            </Text>
          </div>
        )}
        {hasStages && items && <TreeSkeleton expandIconsOffset={8} items={items} timeline />}
      </ScrollableArea>
    );
  }

  return (
    <div className="pipeline-details-content">
      <SyncDetailsRefresh onRefresh={fetchDetails} isRefreshing={loading} />
      <Spacer y="sm" />
      {content}
    </div>
  );
};
export interface EntityDetailsProps {
  entityId: string;
  entityName: string;
  visible: boolean;
}

const EntityDetails = ({ entityId, entityName, visible }: EntityDetailsProps) => {
  const dispatch = useDispatch();

  const connectorMetadata = useSelector(selectConnectorsMetadata);
  const connectors = useSelector(selectAllConnectors);

  const [fetchMetrics, { data: metrics, isError, isFetching }] = useLazyGetEntitySyncMetricsQuery();

  const fetchDetails = useCallback(() => {
    if (entityId && entityName) {
      fetchMetrics({ syncariEntityId: entityId });
      dispatch(getSyncStatus(entityId));
    }
  }, [dispatch, entityId, entityName, fetchMetrics]);

  useEffect(() => {
    if (visible) {
      fetchDetails();
    }
  }, [fetchDetails, visible]);

  const metaDataMap = keyBy(connectorMetadata, 'id');
  const iconMap: Record<string, string | null> = {};

  connectors?.forEach((connector) => {
    const metadata = metaDataMap[connector.metadataId];
    if (metadata) {
      iconMap[connector.id] = metadata.iconUri;
    }
  });

  return (
    <EntityDetailsContent
      entityId={entityId}
      metrics={metrics}
      loading={isFetching}
      iconMap={iconMap}
      hasError={isError}
      fetchDetails={fetchDetails}
    />
  );
};

export default EntityDetails;
