//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, useMatch } from '@reach/router';
import { Moment } from 'moment-timezone';
import { useCallback, useRef, useState, useEffect } from 'react';

import { withI18n } from 'components/I18nProvider';
import { HStack, Stack } from 'components/layout';
import { SearchInput } from 'components/SearchInput';
import { TableFilterProvider, TableFilterProviderRef } from 'components/TableFilters';
import { tNamespaced, tc } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import Button from 'components/button-component/Button';
import Modal from 'components/Modal';
import Spinner from 'components/Spinner';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { message } from 'antd';
import { FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { getEntityPipeline } from 'actions/entityPipelineActions';
import { usePipelineSettings } from '../settings/Settings.hooks';
import { useGetDFIProvisionStatusQuery, usePatchPipelineSettingsMutation } from 'store/data-quality-v2/api';
import { Tooltip } from 'antd';

import CategoriesModal from './category/CategoriesModal';
import { CategoriesContextProvider } from './category/CategoriesTable.context';
import { useDataQuality } from './DataQuality.hooks';
import { DataQualityAction } from './rules/DataQualityAction';
import RulesModal from './rules/RulesModal';
import { RulesTable } from './rules/RulesTable';
import CenterLayout from 'components/layout/CenterLayout';
import AppConstants from 'utils/AppConstants';
import './DataQuality.scss';

export interface DataQualityFilterValues {
  name?: string;
  startDate: Moment;
  endDate: Moment;
}

export const defaultPipelineLogsFilters = {
  name: '',
};

export interface DataQualityProps {
  entityId?: string;
  path?: string;
}

const tn = tNamespaced('DataQuality');

export const DataQuality = withI18n(({ entityId }: DataQualityProps) => {
  const filterPanelRef = useRef<TableFilterProviderRef | null>(null);
  const pipelineMatch = useMatch('/sync-studio/entity/:entityId/data-quality/:graphVersion/*');
  const dispatch = useEnhancedDispatch();
  const pipeline = useEnhancedSelector((state) => state.entityPipeline.entityPipeline);
  const isLoading = useEnhancedSelector((state) => state.entityPipeline.entityPipelineFetching);
  const isError = useEnhancedSelector((state) => state.entityPipeline.entityPipelineError);
  const settings = pipeline?.settings;
  const [patchPipelineSettings] = usePatchPipelineSettingsMutation();
  const { editable } = useDataQuality();
  const { isDraft } = usePipelineSettings();
  const [enableConfirmationVisible, setEnableConfirmationVisible] = useState(false);
  const [isEnablingDataQuality, setIsEnablingDataQuality] = useState(false);
  const [shouldPoll, setShouldPoll] = useState(false);
  const [filter, setFilter] = useState<Partial<DataQualityFilterValues>>(defaultPipelineLogsFilters);

  const {
    data: dfiProvisionStatus,
    isFetching: isFetchingDfiProvisionStatus,
    refetch: refetchDfiProvisionStatus,
  } = useGetDFIProvisionStatusQuery(
    {
      syncariEntityId: entityId || '',
      draftStatus: ['draft', 'new'].includes(pipelineMatch?.graphVersion?.toLowerCase() || '') ? 'NEW' : 'APPROVED',
    },
    {
      skip: !entityId || !pipelineMatch?.graphVersion,
      pollingInterval: shouldPoll ? AppConstants.POLLING_INTERVAL_MS : 0,
    }
  );

  useEffect(() => {
    setShouldPoll(dfiProvisionStatus?.status === 'inProgress' || isEnablingDataQuality);
  }, [dfiProvisionStatus?.status, isEnablingDataQuality]);

  useEffect(() => {
    if (dfiProvisionStatus?.status === 'enabled') {
      setIsEnablingDataQuality(false);
    }
  }, [dfiProvisionStatus?.status, setIsEnablingDataQuality]);

  useEffect(() => {
    if (entityId && pipelineMatch?.graphVersion) {
      dispatch(getEntityPipeline(entityId, pipelineMatch.graphVersion as 'NEW' | 'APPROVED' | undefined));
    }
  }, [dispatch, entityId, pipelineMatch?.graphVersion]);

  const showManageRules = useCallback(() => {
    if (pipelineMatch?.graphVersion) {
      navigate(makeUrl(RouteConstants.DATA_QUALITY_RULES, { entityId, graphVersion: pipelineMatch.graphVersion }));
    }
  }, [entityId, pipelineMatch?.graphVersion]);

  const showManageCategories = useCallback(() => {
    if (pipelineMatch?.graphVersion) {
      navigate(makeUrl(RouteConstants.DATA_QUALITY_CATEGORIES, { entityId, graphVersion: pipelineMatch.graphVersion }));
    }
  }, [entityId, pipelineMatch?.graphVersion]);

  const handleEnableDataQuality = useCallback(async () => {
    try {
      const graph = isDraft ? pipeline?.draft || pipeline : pipeline;

      if (!isDraft && !pipeline?.draft) {
        message.error(tn('no_draft_error'));
        setIsEnablingDataQuality(false);
        return;
      }
      // Enable data quality
      await patchPipelineSettings({
        entityId: entityId!,
        payload: {
          ...graph,
          settings: {
            ...settings,
            dataQuality: true,
          },
        },
      }).unwrap();

      dispatch(getEntityPipeline(entityId!, pipelineMatch?.graphVersion as 'NEW' | 'APPROVED' | undefined));
      refetchDfiProvisionStatus();
      setEnableConfirmationVisible(false);
      setIsEnablingDataQuality(true);
    } catch (error) {
      message.error(getRtkQueryErrorMessage(error as FetchBaseQueryError));
      setIsEnablingDataQuality(false);
    }
  }, [
    entityId,
    patchPipelineSettings,
    dispatch,
    pipelineMatch?.graphVersion,
    settings,
    isDraft,
    pipeline,
    refetchDfiProvisionStatus,
  ]);

  if (isLoading || isFetchingDfiProvisionStatus || isEnablingDataQuality || !dfiProvisionStatus?.status) {
    return (
      <CenterLayout>
        <Spinner />
      </CenterLayout>
    );
  }

  if (dfiProvisionStatus?.status === 'inProgress') {
    return (
      <div className="data-quality">
        <div className="data-quality__in-progress-message">
          <Spinner />
          {tn('data_quality_in_progress')}
        </div>
      </div>
    );
  }

  if (dfiProvisionStatus?.status === 'disabled') {
    return (
      <div className="data-quality">
        <div className="data-quality__disabled-message">
          {tn('data_quality_disabled')}
          <Tooltip title={isError && tn('no_draft_tooltip')} trigger="hover">
            <div>
              <Button
                type="primary"
                onClick={() => setEnableConfirmationVisible(true)}
                loading={isEnablingDataQuality}
                disabled={isError}>
                {tn('enable_data_quality')}
              </Button>
            </div>
          </Tooltip>
          <Modal
            visible={enableConfirmationVisible}
            onCancel={() => setEnableConfirmationVisible(false)}
            title={tn('enable_data_quality_title')}
            okText={tc('enable')}
            onOk={handleEnableDataQuality}
            confirmLoading={isEnablingDataQuality}>
            {tn('enable_data_quality_confirmation')}
          </Modal>
        </div>
      </div>
    );
  }

  return (
    <TableFilterProvider ref={filterPanelRef} initiallyVisible>
      <Stack className="data-quality" fill spacing="lg">
        <div className="data-quality__filter-container">
          <HStack justify="space-between" grow>
            <HStack>
              <SearchInput
                defaultValue={filter.name}
                onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                  setFilter({ name: evt.target.value });
                }}
              />
            </HStack>
            <HStack spacing="sm">
              <DataQualityAction>
                <Button type="primary" disabled={!editable} onClick={showManageRules}>
                  {tn('create_rule')}
                </Button>
              </DataQualityAction>
              <Button onClick={showManageCategories}>{tn('manage_categories')}</Button>
            </HStack>
          </HStack>
        </div>
        {entityId && (
          <>
            <RulesTable entityId={entityId} filter={filter} />
            <CategoriesContextProvider>
              <CategoriesModal />
            </CategoriesContextProvider>
            <RulesModal />
          </>
        )}
      </Stack>
    </TableFilterProvider>
  );
}, 'DataQuality');
