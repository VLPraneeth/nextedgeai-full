import React, { useEffect, useMemo, useState } from 'react';
import { Redirect, RouteComponentProps, Router, useLocation, useMatch, useNavigate } from '@reach/router';
import { Tooltip } from 'antd';
import Icon from 'antd/lib/icon';
import Spin from 'antd/lib/spin';

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
import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
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
import { ReferenceDataGrid, ReferenceDataSidebarSection } from './ReferenceData';
import {
  DataGridDropdownSectionLinkItem,
  DataGridDropdownItemBadge,
  DataGridDropdownSection,
  DataGridDropdownSectionItem,
} from './DataGridDropdown';
import { Entity } from 'store/entity/types';
import SearchBox from 'components/SearchBox';
import { useReferenceDataList } from 'store/reference-data/hooks';
import ReferenceDataUpsertPanel from './ReferenceData/ReferenceDataUpsertPanel';
import RecordLineage from './Lineage/Lineage';

type DataStudioRootProps = RouteComponentProps<{ entityId?: string }>;

const DataStudioRoot = (props: RouteComponentProps) => {
  const { tn } = useI18nContext();
  const location = useLocation();
  const [expandedSections, setExpandedSections] = useState({
    entities: true,
    referenceData: false,
  });

  // Check if we're on lineage page
  const isLineagePage = useMatch('/data-studio/entity/:entityId/record/:recordId/lineage');

  // Extract entityId and refDataId early so they can be used in hooks
  const entityIdMatch = useMatch('/data-studio/entity/:entityId/*');
  const refDataIdMatch = useMatch('/data-studio/reference-data/:refDataId/*');
  const entityId = entityIdMatch?.entityId;
  const refDataId = refDataIdMatch?.refDataId;

  // Update expanded section based on URL
  useEffect(() => {
    if (entityId) {
      setExpandedSections((prev) => ({
        ...prev,
        entities: true,
      }));
    } else if (location.pathname.includes('reference-data')) {
      setExpandedSections((prev) => ({
        ...prev,
        referenceData: true,
      }));
    }
  }, [location.pathname, entityId]);

  const toggleSection = (section: 'entities' | 'referenceData') => {
    setExpandedSections((prev) => ({
      ...prev,
      [section]: !prev[section],
    }));
  };

  const Error403 = useForbiddenRedirect({
    studioPermissions: [AllPermissions.READ_DATA_STUDIO],
    operator: PermissionsComparisonOperator.AND,
  });
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [showingCreateModal, setShowingCreateModal] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  const handleSearch = (event: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(event.target.value);
  };

  const { loading: entitiesLoading, data: entities } = useSyncariEntities();
  const {
    recordCounts: entityRecordCounts,
    fetchStatus: entityRecordsFetchStatus,
    fetchEntitiesCount,
  } = useEntityRecordsCount(entities.map((entity) => entity.apiName));

  const { userHasPermission } = useUserHasPermission();

  const { data: dataStore } = useGetDataStoreQuery();
  const { data: referenceDataList } = useReferenceDataList();

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

  const filteredEntities = useMemo(() => {
    const term = searchTerm.toLowerCase() || '';
    return entities?.filter(
      (entity) => entity.displayName.toLowerCase().includes(term) || entity.apiName.toLowerCase().includes(term)
    );
  }, [entities, searchTerm]);

  const filteredReferenceData = useMemo(() => {
    if (!searchTerm.trim()) return referenceDataList;
    const term = searchTerm.toLowerCase() || '';
    return referenceDataList?.filter(
      (item) => item.name.toLowerCase().includes(term) || item.key?.toLowerCase().includes(term)
    );
  }, [referenceDataList, searchTerm]);

  // Auto-expand sections based on search results
  useEffect(() => {
    if (searchTerm.trim()) {
      const hasEntityMatches = filteredEntities && filteredEntities.length > 0;
      const hasReferenceDataMatches = filteredReferenceData && filteredReferenceData.length > 0;

      setExpandedSections({
        entities: hasEntityMatches,
        referenceData: hasReferenceDataMatches,
      });
    } else {
      // When search is cleared, reset to default state
      setExpandedSections({
        entities: true,
        referenceData: false,
      });
    }
  }, [searchTerm]);

  const { data: bookmarkedFiltersData } = useEntityFiltersList({
    count: 100,
    direction: 'next',
    bookmarked: false,
  });

  // Define getValueName useMemo before the early return to ensure hooks are always called
  const getValueName = useMemo(() => {
    let displayName = '';
    let count: number | null | undefined | string = null;

    // Priority 1: Check if an entity is selected
    if (entityId) {
      const selectedEntity = entities?.find((entity) => entity.id === entityId);
      if (selectedEntity) {
        displayName = selectedEntity.displayName;
        count = entityRecordCounts?.[selectedEntity.apiName];
      } else {
        displayName = entityId;
      }
    }
    // Priority 2: Check if a reference data item is selected
    else if (refDataId) {
      const selectedRefData = referenceDataList?.find((refData) => refData.id === refDataId);
      if (selectedRefData) {
        displayName = selectedRefData.name;
        count = selectedRefData.totalRecords;
      } else {
        // Check if it's a bookmarked filter
        const selectedFilter = bookmarkedFiltersData?.filters?.find((filter) => filter.id === refDataId);
        if (selectedFilter) {
          displayName = selectedFilter.name;
          count = null;
        }
      }
    }

    return (
      <div className="data-studio-select-value">
        <p>{displayName ?? ''}</p>
        {count !== null && count !== undefined && <span> ({count ?? 0} records)</span>}
      </div>
    );
  }, [entityId, entities, entityRecordCounts, refDataId, bookmarkedFiltersData?.filters, referenceDataList]);

  // Early return AFTER all hooks have been called
  if (!entityId && entities?.length > 0 && !refDataId) {
    // if we don't have an entity selection,
    // redirect to the first Entity in our entities list
    return <Redirect to={makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: entities[0].id })} noThrow />;
  }

  const renderEntityOptions = (entity: Entity) => {
    const count = entityRecordCounts?.[entity.apiName];
    const lagMap = entitiesLagsMap?.[entity.apiName];
    const hasCountError = entityRecordsFetchStatus?.[entity.apiName] === AppConstants.FETCH_STATUS.ERROR;

    const label = (
      <DataGridDropdownSectionLinkItem
        highlightPartialMatch
        key={entity?.id}
        to={makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId: entity.id })}>
        <HStack justify="space-between" align="baseline" spacing="xxs">
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
            {lagMap?.count ? (
              <Popover
                placement="right"
                arrowPointAtCenter
                overlayClassName="lag-icon-tooltip"
                trigger="hover"
                content={
                  <>
                    {lagMap?.timestamp && (
                      <div className="data-studio-item__lag-timestamp">
                        {tn('data_lag_timestamp')} {format(lagMap?.timestamp, SHORT_DATE_TIME_DISPLAY_FORMAT)}
                      </div>
                    )}
                    <div>
                      <span className="data-studio-item__lag-count">{`${numberFormat(lagMap?.count)}`}</span>
                      {tn('records_pending')}
                    </div>
                  </>
                }>
                <Icon component={(props) => <ExclamationIcon {...props} width={18} height={18} />} />
              </Popover>
            ) : null}
          </span>
          <HStack>
            {hasCountError ? (
              <Tooltip
                title={tn('count_fetch_failed')}
                className="data-studio-item-count-error"
                placement="top"
                mouseEnterDelay={0}>
                <Icon type="exclamation-circle" />
              </Tooltip>
            ) : (
              <DataGridDropdownItemBadge>
                <NumberText>{count}</NumberText>
              </DataGridDropdownItemBadge>
            )}
          </HStack>
        </HStack>
      </DataGridDropdownSectionLinkItem>
    );

    return label;
  };

  return (
    Error403 ?? (
      <div className="data-studio-container">
        <div className="data-studio-main-content">
          {!isLineagePage && (
            <Popover
              trigger="click"
              placement="bottomLeft"
              overlayClassName="data-studio-entity-picker-popover"
              visible={isDropdownOpen}
              onVisibleChange={(visible) => {
                setIsDropdownOpen(visible);
                setSearchTerm('');
                setExpandedSections({ entities: true, referenceData: false });
              }}
              content={
                <>
                  <SearchBox
                    placeholder="Search"
                    data-testid="ds-search-input"
                    value={searchTerm}
                    size="default"
                    onChange={handleSearch}
                    style={{ marginBottom: '8px' }}
                    onKeyDown={(e) => {
                      if (e.key === 'Escape') {
                        setIsDropdownOpen(false);
                      }
                    }}
                    autoFocus
                  />
                  <div className="data-studio-entity-picker-content">
                    {/* Entities Section */}
                    <DataGridDropdownSection
                      title={`Entities (${entities?.length || 0})`}
                      isExpanded={expandedSections.entities}
                      onToggle={() => toggleSection('entities')}>
                      {entitiesLoading ? (
                        <Spin key="entities-loading" size="small" spinning={entitiesLoading} />
                      ) : filteredEntities?.length > 0 ? (
                        filteredEntities?.map((option) => (
                          <div
                            key={option.id}
                            onClick={() => {
                              setIsDropdownOpen(false);
                              setSearchTerm('');
                            }}>
                            <HStack spacing="xs">{renderEntityOptions(option)}</HStack>
                          </div>
                        ))
                      ) : (
                        <DataGridDropdownSectionItem key="no-entities">
                          <TranslatedText style={{ padding: '0 12px' }} text="no_entities_sidebar_item" />
                        </DataGridDropdownSectionItem>
                      )}
                    </DataGridDropdownSection>

                    {/* Reference Data Section */}
                    <DataGridDropdownSection
                      title={`Reference Data (${referenceDataList?.length || 0})`}
                      isExpanded={expandedSections.referenceData}
                      onToggle={() => toggleSection('referenceData')}>
                      <ReferenceDataSidebarSection
                        searchTerm={searchTerm}
                        onSelect={() => {
                          setIsDropdownOpen(false);
                          setSearchTerm('');
                        }}
                      />
                    </DataGridDropdownSection>

                    {/* Create New Filter Button */}
                    {userHasPermission(AllPermissions.WRITE_REFERENCE_DATA) && (
                      <div
                        style={{
                          borderTop: '1px solid #f0f0f0',
                          padding: '10px 0',
                          marginTop: '8px',
                          cursor: 'pointer',
                        }}
                        onClick={() => {
                          setShowingCreateModal(true);
                          setIsDropdownOpen(false);
                        }}>
                        <HStack spacing="sm" align="center">
                          <span style={{ color: '#1890ff' }}>Add new Reference data</span>
                          <Icon type="caret-right" style={{ color: '#1890ff' }} />
                        </HStack>
                      </div>
                    )}
                  </div>
                </>
              }>
              <div className="data-studio-entity-picker-trigger" onClick={() => setIsDropdownOpen(!isDropdownOpen)}>
                <div data-testid="data-studio-select-input" className="data-studio-select-input">
                  {getValueName}
                  <Icon type={isDropdownOpen ? 'up' : 'down'} className="data-studio-select-icon" />
                </div>
              </div>
            </Popover>
          )}
          <Router>
            <DataStudioGrid
              key={entityId}
              path="/entity/:entityId/*"
              entityId={entityId!}
              onRecordCountChange={fetchEntitiesCount}
            />
            <ReferenceDataGrid key={refDataId} path="/reference-data/:refDataId/*" />
            <RecordLineage path="/entity/:entityId/record/:recordId/lineage" entityId={entityId as string} />
            <EmptyState default loading={entitiesLoading} />
          </Router>
        </div>
        <ReferenceDataUpsertPanel onRequestClose={() => setShowingCreateModal(false)} visible={showingCreateModal} />
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
