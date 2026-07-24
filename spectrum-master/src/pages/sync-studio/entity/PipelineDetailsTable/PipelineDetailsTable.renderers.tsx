import { Link } from '@reach/router';
import { ColDef } from 'ag-grid-community';
import { Tooltip } from 'antd';
import cx from 'classnames';
import { upperFirst } from 'lodash/fp';
import { Moment } from 'moment';
import { ReactNode } from 'react';

import { ReactComponent as Warning } from 'assets/icons/warning.svg';
import InlineSvg from 'components/icons/InlineSvg';
import KebabMenu from 'components/KebabMenu';
import DateCellRenderer from 'components/renderers/DateCellRenderer';
import Spinner from 'components/Spinner';
import { TextTag } from 'components/text-tag';
import { TextTagColorOptions } from 'components/text-tag/TextTag';
import { Text } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { useGetPipelinesSyncMetricDetailsQuery, useGetPipelinesTransactionDetailsQuery } from 'store/pipeline/api';
import { SinkOrSourceDetails } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { PipelineDetailsPanelNames } from '../PipelineDetails/PipelineDetails.constants';
import { usePipelineDetailsSearchParameters } from '../PipelineDetails/PipelineDetails.hooks';
import { useCurrentSyncStudioRootTab } from '../SyncStudioRootTabs';
import { useCurrentActivityText, usePipelineActionsCell } from './PipelineDetailsTable.hooks';
import { getPipelineStateObject, getPipelineStatusObject } from './PipelineDetailsTable.utils';

import './PipelineDetailsTable.renderers.scss';

const tn = tNamespaced('PipelineDetailsTable');

const TOOLTIP_DELAY = 0.5;

export interface CellRendererProps<T> {
  value: T;
  data: Record<string, any>;
  colDef: ColDef;
}

export interface TagData {
  label: string;
  color: TextTagColorOptions;
  url: string | undefined;
}

export interface ModifiedByData {
  userId: string;
  firstName: string;
  lastName: string;
}

export interface SyncDurationData {
  duration: string;
  durationUnit: string;
}

/**
 * Helper Components
 *   These components will be used to enhance the typical functionality of the
 *   cell renderers (i.e. handling a cell specific on click action).
 */

const ActionCellHelper = ({ children, onClick }: { onClick?: () => void; children?: ReactNode }) => {
  return (
    <Tooltip className="truncated-cell" title={children} mouseEnterDelay={TOOLTIP_DELAY}>
      <a
        onClick={(e) => {
          e.stopPropagation();

          onClick?.();
        }}>
        {children ?? '-'}
      </a>
    </Tooltip>
  );
};

const LinkCellHelper = ({ children, url }: { url: string; children?: ReactNode }) => {
  return (
    <Tooltip className="truncated-cell" title={children} mouseEnterDelay={TOOLTIP_DELAY}>
      <Link to={url}>{children ?? '-'}</Link>
    </Tooltip>
  );
};

/**
 * Main Renderers
 *   These renderers are ready to go and do not need any additional enhancement.
 */

export const PlaceholderCell = ({ value }: CellRendererProps<any>) => {
  return (
    <Tooltip className="truncated-cell" title={value} mouseEnterDelay={TOOLTIP_DELAY}>
      {value ?? '-'}
    </Tooltip>
  );
};

export const NameCell = ({ value, data }: CellRendererProps<string>) => {
  const url = makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { graphVersion: 'new', entityId: data.id });

  return <LinkCellHelper url={url}>{value}</LinkCellHelper>;
};

export const TagCell = ({ value, data, colDef }: CellRendererProps<TagData[]>) => {
  const tags = value.map((tagData) => {
    // Special tag for warning
    if (tagData.label.toLowerCase() === 'warning') {
      return (
        <div className="tag-cell--warning">
          <Warning width={16} height={16} />
        </div>
      );
    }
    let tag = <TextTag text={tagData.label} color={tagData.color} />;

    if (tagData.url) {
      tag = (
        <Link className="badge-cell__link" key={tagData.label} to={tagData.url}>
          {tag}
        </Link>
      );
    }
    return tag;
  });

  // If the pipeline status is empty, then disregard the
  // tag data for the pipeline status.
  if (data.pipelineStatusTagData.length === 0 && colDef.field === 'pipelineStateTagData') {
    return <span>-</span>;
  }

  if (!value || value.length === 0) {
    // If the pipeline status if empty, display an Unmapped tag instead
    if (colDef.field === 'pipelineStatusTagData') {
      tags.push(
        <Link
          className="badge-cell__link"
          key={tn('unmapped')}
          to={makeUrl(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, { entityId: data.id, graphVersion: 'new' })}>
          <TextTag text={tn('unmapped')} color="gray" />
        </Link>
      );
    } else {
      return <span>-</span>;
    }
  }

  return <div className="badge-cell">{tags}</div>;
};

export const PipelineStatusCell = ({ value, data, colDef }: CellRendererProps<TagData[]>) => {
  const { getSyncStatusesStatus } = useEnhancedSelector((state) => state.entityPipeline);

  if (getSyncStatusesStatus === AppConstants.FETCH_STATUS.LOADING) {
    return <Spinner />;
  }
  return <TagCell value={value} data={data} colDef={colDef} />;
};

export const TimeCell = ({ value }: CellRendererProps<Moment>) => {
  if (!value) {
    return '-';
  }

  return <div className="truncated-cell">{DateCellRenderer(value)}</div>;
};

export const ActivityCell = ({ value, data }: CellRendererProps<string>) => {
  const { isLoading } = useGetPipelinesSyncMetricDetailsQuery();
  const { updateSearchParams } = usePipelineDetailsSearchParameters();
  const { currentTab } = useCurrentSyncStudioRootTab();
  const text = useCurrentActivityText(value, data.pipelineStatusTagData, data.pipelineStateTagData, data.settings);

  const baseUrl = makeUrl(RouteConstants.ENTITY, { entityId: data.id, tabId: currentTab });

  const handleOpenActivity = () => {
    const params = [{ name: 'panel', value: PipelineDetailsPanelNames.ACTIVITY }];

    updateSearchParams(params, baseUrl);
  };

  const { isPublished } = getPipelineStatusObject(data.pipelineStatusTagData);
  const { isPaused } = getPipelineStateObject(data.pipelineStateTagData);
  const shouldWaitForFetch = isPublished && !isPaused;

  // Only show a loading state if we can't determine the activity from the UI.
  if (isLoading && shouldWaitForFetch) {
    return <Spinner />;
  }

  return isPublished ? (
    <ActionCellHelper onClick={handleOpenActivity}>{text}</ActionCellHelper>
  ) : (
    <Tooltip className="truncated-cell" title={text} mouseEnterDelay={TOOLTIP_DELAY}>
      {text}
    </Tooltip>
  );
};

export const TransactionsCell = ({ value, data }: CellRendererProps<string>) => {
  const { isLoading } = useGetPipelinesTransactionDetailsQuery();

  const url = makeUrl(RouteConstants.LOGS_TRANSACTIONS, {}, { entityName: data.apiName });

  if (isLoading) {
    return <Spinner />;
  }

  return <LinkCellHelper url={url}>{value}</LinkCellHelper>;
};

export const VersionsCell = ({ value, data }: CellRendererProps<string>) => {
  const url = makeUrl(RouteConstants.ENTITY_VERSIONS, { entityId: data.id });

  return <LinkCellHelper url={url}>{value}</LinkCellHelper>;
};

export const PipelineActionsCell = ({ data }: CellRendererProps<never>) => {
  const { menuItems, menuActionHandler } = usePipelineActionsCell(data);

  return <KebabMenu onClick={menuActionHandler} menuItems={menuItems} />;
};

export const SinkSourceCell = ({ value, data }: CellRendererProps<SinkOrSourceDetails[]>) => {
  const { connectors } = useEnhancedSelector((state) => state.connector);

  const connectorMetadataMap = useConnectorIdToMetadataMap();

  const badges = value?.map((sinkOrSource) => {
    const connector = connectors?.find((connector) => {
      return connector.name.toLowerCase() === sinkOrSource.connectorName.toLowerCase();
    });

    const isSyncariConnector = sinkOrSource.connectorName.toLowerCase() === 'syncari';
    const url = makeUrl(RouteConstants.SCHEMA_STUDIO_SYNAPSE, { connectorId: connector?.id });

    const showDraft = connectorIsCustomDraft(connectorMetadataMap[sinkOrSource.connectorId]);

    return (
      <Link className="sink-source-cell" to={url}>
        {connector && (
          <InlineSvg
            className={cx('sink-source-cell__icon', isSyncariConnector && 'sink-source-cell__icon--bigger')}
            title={sinkOrSource.connectorName}
            src={connector.iconUri}
          />
        )}
        <Text className="sink-source-cell__connector" lineHeight="snug" size="sm">
          {isSyncariConnector ? upperFirst(sinkOrSource?.connectorName) : sinkOrSource?.connectorName}
        </Text>
        <Text className="sink-source-cell__divider" lineHeight="snug" size="sm">
          /
        </Text>
        <Text className="sink-source-cell__entity" lineHeight="snug" size="sm">
          {sinkOrSource?.entityName}
        </Text>
        {showDraft && <TextTag text="Draft" color="orange" className="tag-inside-pill" />}
      </Link>
    );
  });

  return badges && badges.length > 0 ? badges : '-';
};

export const MergeCell = ({ value }: CellRendererProps<string>) => {
  const mergeValue = value ? tc('enabled') : tc('disabled');

  return (
    <Tooltip className="truncated-cell" title={mergeValue} mouseEnterDelay={TOOLTIP_DELAY}>
      {mergeValue}
    </Tooltip>
  );
};

export const UserCell = ({ value }: CellRendererProps<ModifiedByData>) => {
  const fullName = value && `${value.firstName} ${value.lastName}`;
  return (
    <Tooltip className="truncated-cell" title={fullName} mouseEnterDelay={TOOLTIP_DELAY}>
      {fullName ?? '-'}
    </Tooltip>
  );
};

export const CycleCell = ({ value }: CellRendererProps<SyncDurationData>) => {
  const { isLoading } = useGetPipelinesSyncMetricDetailsQuery();

  if (isLoading) {
    return <Spinner />;
  }

  const cycleDuration = value && `${value.duration} ${value.durationUnit.toLowerCase()}`;

  return (
    <Tooltip className="truncated-cell" title={cycleDuration} mouseEnterDelay={TOOLTIP_DELAY}>
      {cycleDuration ?? '-'}
    </Tooltip>
  );
};

export const frameworkComponents = {
  activity: ActivityCell,
  cycle: CycleCell,
  merge: MergeCell,
  name: NameCell,
  pipelineActions: PipelineActionsCell,
  placeholder: PlaceholderCell,
  sinkSource: SinkSourceCell,
  tag: TagCell,
  pipelineStatus: PipelineStatusCell,
  time: TimeCell,
  transactions: TransactionsCell,
  user: UserCell,
  versions: VersionsCell,
};
