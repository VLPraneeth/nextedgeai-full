import { Redirect, RouteComponentProps, Router, useMatch } from '@reach/router';
import { Tooltip } from 'antd';
import Icon from 'antd/lib/icon';
import Spin from 'antd/lib/spin';
import { map } from 'lodash';
import * as React from 'react';

import { ReactComponent as ExclamationIcon } from 'assets/icons/exclamation.svg';
import { ReactComponent as GroupIcon } from 'assets/images/group.svg';
import Button from 'components/Button';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import Popover from 'components/Popover';
import { NumberText, TranslatedText } from 'components/typography';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import { useEntityRecordsCount, useSyncariEntities } from 'hooks/useSyncariEntities';
import { PermissionsComparisonOperator } from 'hooks/useUserHasPermission';
import { useCurrentSyncStudioRootTab } from 'pages/sync-studio/entity/SyncStudioRootTabs';
import { useEntityFiltersList } from 'store/data-studio/hooks';
import { useGetDataStoreLagQuery, useGetDataStoreQuery } from 'store/datastore/api';
import AppConstants from 'utils/AppConstants';
import { format, SHORT_DATE_TIME_DISPLAY_FORMAT } from 'utils/DateUtil';
import { createUniqueEntityTitle } from 'utils/FieldUtil';
import { numberFormat } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import './DataStudio.less';
import DataStudioGrid from './DataStudioGrid';
import DataStudioRecordDetail from './RecordDetail';
import { ReferenceDataGrid, ReferenceDataSidebarSection } from './ReferenceData';
import Sidebar, { SidebarItemBadge, SidebarSection, SidebarSectionItem, SidebarSectionLinkItem } from './Sidebar';

type DataStudioRootProps = RouteComponentProps<{ entityId?: string }>;

const DataStudioRoot = () => {
  const { tn } = useI18nContext();

  const Error403 = useForbiddenRedirect({
    studioPermissions: [AllPermissions.READ_DATA_STUDIO],
    operator: PermissionsComparisonOperator.AND,
  });

  const entityIdMatch = useMatch('/data-studio/entity/:entityId/*');
  const refDataIdMatch = useMatch('/data-studio/reference-data/:refDataId/*');

  const { loading: entitiesLoading, data: entities } = useSyncariEntities();
  const {
    recordCounts: entityRecordCounts,
    fetchStatus: entityRecordsFetchStatus,
    totalCounts,
    fetchEntitiesCount,
  } = useEntityRecordsCount(entities.map((entity) => entity.apiName));

  const { data: dataStore } = useGetDataStoreQuery();

  const { data: lags } = useGetDataStoreLagQuery(undefined, {
    skip: !dataStore,
  });

  const entitiesLagsMap = React.useMemo(
    () =>
      lags?.reduce<
        Record<
          string,
          {
            count: number;
            timestamp: string;
          }
        >
      >((acc, lag) => {
        acc[lag.entityName] = {
          count: lag.pendingRecords,
          timestamp: lag.dataStoreCurrentTimestamp,
        };
        return acc;
      }, {}) || {},
    [lags]
  );

  useMountUnmountEffect(() => fetchEntitiesCount(true));

  const { loading: bookmarkedFiltersLoading, data: bookmarkedFiltersData } = useEntityFiltersList({
    count: 100,
    direction: 'next',
    bookmarked: true,
  });

  const entityId = entityIdMatch?.entityId;
  const refDataId = refDataIdMatch?.refDataId;

  if (!entityId && entities?.length > 0 && !refDataId) {
    // if we don't have an entity selection,
    // redirect to the first Entity in our entities list
    return <Redirect to={makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: entities[0].id })} noThrow />;
  }

  return (
    Error403 ?? (
      <div className="data-studio-container">
        <Sidebar>
          <SidebarSection title={tn('favorite_filters_title')} className="data-studio-sidebar-filters-section">
            {bookmarkedFiltersData && bookmarkedFiltersData.filters?.length > 0 ? (
              bookmarkedFiltersData.filters.map((filter) => (
                <SidebarSectionLinkItem
                  key={filter.id}
                  to={`${makeUrl(
                    RouteConstants.DATA_STUDIO_ENTITY,
                    {
                      entityId: filter.syncariEntityId,
                    },
                    { filterId: filter.id }
                  )}`}>
                  <HStack spacing="xs" align="start">
                    <Icon type="star" theme="filled" className="favorite-filter-icon" />
                    <span>{filter.name}</span>
                  </HStack>
                </SidebarSectionLinkItem>
              ))
            ) : bookmarkedFiltersLoading ? (
              <Spin size="small" spinning />
            ) : (
              <SidebarSectionItem key="empty">
                <TranslatedText text="favorite_filters_empty_text" />
              </SidebarSectionItem>
            )}
          </SidebarSection>
          <SidebarSection title={tn('entities_list_title')}>
            {map(totalCounts, (count, key) => {
              const formattedCount = numberFormat(count);

              return (
                <Tooltip
                  key={key}
                  title={tn(`TotalRecordsSummary.${key}_tooltip`, { count: formattedCount })}
                  placement="top"
                  mouseEnterDelay={1}>
                  <div>
                    <SidebarSectionItem className="data-studio-sidebar-section-item-link-wrapper">
                      <HStack justify="space-between" grow>
                        <span className="data-studio-item-title-truncated">
                          {tn(`TotalRecordsSummary.total_${key}`)}
                        </span>
                        <div className={`data-studio-sidebar-item-badge ${key}`}>
                          <NumberText>{count}</NumberText>
                        </div>
                      </HStack>
                    </SidebarSectionItem>
                  </div>
                </Tooltip>
              );
            })}

            {/* Gray divider that separates the totals from the entities */}
            <div className="data-studio-sidebar-filters-section__spacer" />

            {entities.length ? (
              entities.map((entity) => {
                const count = entityRecordCounts?.[entity.apiName];
                const lagMap = entitiesLagsMap?.[entity.apiName];
                const hasCountError = entityRecordsFetchStatus?.[entity.apiName] === AppConstants.FETCH_STATUS.ERROR;

                return (
                  <SidebarSectionLinkItem
                    highlightPartialMatch
                    key={entity.id}
                    to={makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: entity.id })}>
                    <div>
                      <HStack justify="space-between">
                        <span className="data-studio-item-title-truncated">
                          <Tooltip
                            title={
                              <span
                                dangerouslySetInnerHTML={{
                                  __html: tn('count_includes', {
                                    name: createUniqueEntityTitle(entity.displayName, entity.apiName),
                                  }),
                                }}
                              />
                            }
                            placement="top"
                            mouseEnterDelay={1}>
                            {entity.displayName}
                          </Tooltip>
                        </span>

                        <HStack>
                          {lagMap?.count && (
                            <Popover
                              placement="right"
                              trigger="hover"
                              title={`${entity.displayName}(${entity.apiName})`}
                              content={
                                <div>
                                  {lagMap?.timestamp && (
                                    <div className="data-studio-item__lag-timestamp">
                                      {tn('data_lag_timestamp')}{' '}
                                      {format(lagMap?.timestamp, SHORT_DATE_TIME_DISPLAY_FORMAT)}
                                    </div>
                                  )}
                                  <div>
                                    <span className="data-studio-item__lag-count">{`<${numberFormat(
                                      lagMap?.count
                                    )}>`}</span>
                                    {tn('records_pending')}
                                  </div>
                                </div>
                              }>
                              <Icon component={(props) => <ExclamationIcon {...props} width={18} height={18} />} />
                            </Popover>
                          )}
                          {hasCountError ? (
                            <Tooltip
                              title={tn('count_fetch_failed')}
                              className="data-studio-item-count-error"
                              placement="top"
                              mouseEnterDelay={0}>
                              <Icon type="exclamation-circle" />
                            </Tooltip>
                          ) : (
                            <SidebarItemBadge>
                              <NumberText>{count}</NumberText>
                            </SidebarItemBadge>
                          )}
                        </HStack>
                      </HStack>
                    </div>
                  </SidebarSectionLinkItem>
                );
              })
            ) : entitiesLoading ? (
              <Spin size="small" spinning={entitiesLoading} />
            ) : (
              <SidebarSectionItem key="empty">
                <TranslatedText text="no_entities_sidebar_item" />
              </SidebarSectionItem>
            )}
          </SidebarSection>
          <ReferenceDataSidebarSection />
        </Sidebar>
        <Router className="data-studio-main-content">
          <DataStudioGrid key={entityId} path="/entity/:entityId" entityId={entityId!} />
          <DataStudioRecordDetail path="/entity/:entityId/record/:recordId/*" entityId={entityId!} />
          <ReferenceDataGrid key={refDataId} path="/reference-data/:refDataId/*" />
          <EmptyState default loading={entitiesLoading} />
        </Router>
      </div>
    )
  );
};

function EmptyState({ navigate, loading }: RouteComponentProps<{ loading: boolean }>) {
  const { currentTab } = useCurrentSyncStudioRootTab();

  if (loading) {
    return <Spin spinning />;
  }

  return (
    <div className="empty-state-container">
      <Stack spacing="lg">
        <Stack spacing="xs">
          <div className="empty-state-icon">
            <GroupIcon />
          </div>
          <div className="empty-state-title">
            <TranslatedText text="no_entities_title" />
          </div>
          <div className="empty-state-content">
            <TranslatedText text="no_entities_body" />
          </div>
        </Stack>
        <Button
          onClick={() => {
            navigate?.(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
          }}
          type="primary">
          <TranslatedText text="no_entities_call_to_action_sync_studio" />
        </Button>
      </Stack>
    </div>
  );
}

export default withI18n<DataStudioRootProps>(DataStudioRoot, 'DataStudio');
