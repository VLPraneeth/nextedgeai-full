//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useMatch } from '@reach/router';
import { Alert, Button, Checkbox, Divider, message, Select, Tooltip } from 'antd';
import cx from 'classnames';
import { Moment } from 'moment-timezone';
import { useEffect, useMemo, useState } from 'react';

import { resyncEntityPipelineForSources, showResyncModal } from 'actions/entityPipelineActions';
import DateTimeSelector from 'components/DateTimeSelector';
import { Stack } from 'components/layout';
import Modal from 'components/Modal';
import Spinner from 'components/Spinner';
import { Text } from 'components/typography';
import useUserLocalMoment from 'hooks/moment';
import {
  useEnhancedDispatch as useDispatch,
  useEnhancedSelector,
  useEnhancedSelector as useSelector,
} from 'hooks/redux';
import useDebouncedFn from 'hooks/useDebouncedFn';
import usePreviousValue from 'hooks/usePreviousValue';
import {
  selectCurrentEntityPipeline,
  selectHasDraft,
  selectSourceEntitiesForCurrentPublishedEntityPipeline,
} from 'selectors/entityPipelineSelectors';
import { useSyncStatusForEntity } from 'selectors/entitySelectors';
import { selectResyncDetails } from 'store/entity-pipeline/selectors';
import { getEntities } from 'store/entity/actions';
import AppConstants from 'utils/AppConstants';
import { SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { mapObj } from 'utils/Fp';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { routeToMatch } from 'utils/StringUtil';

import './EntitySyncStatusMenuItem.less';
import './ResyncRequestModal.scss';

const tn = tNamespaced('EntitySyncStatus');
const { FETCH_STATUS } = AppConstants;

const ALL_RECORDS = 'all';
const AFTER_DATE = 'after';
const BETWEEN_DATES = 'between';

// TODO: readd support for BETWEEN later
const DateScopes = [ALL_RECORDS, AFTER_DATE /* , BETWEEN_DATES */];

const initialDates: Record<string, Moment | undefined> = { fromDate: undefined, toDate: undefined };
const initialErrors: Record<string, boolean | null> = { sources: null, fromDate: null, toDate: null };

// invert :: Func -> a -> Boolean
const invert = (fn: Function) => (...args: unknown[]) => !fn(...args);

// toJSON :: Moment -> String | null
const toJSON = (m?: Moment) => m?.toJSON();

const datesToJson = mapObj(toJSON);

export function useResyncStates() {
  const resyncDetails = useSelector(selectResyncDetails);
  const entityPipeline = useSelector(selectCurrentEntityPipeline);
  const pipelineHasDraft = useSelector(selectHasDraft);
  const lastSynctime = useSelector((state) => state.entityPipeline.lastSyncedTime);
  const sourceEntities = useSelector(selectSourceEntitiesForCurrentPublishedEntityPipeline);
  const prevLastSyncTime = usePreviousValue(lastSynctime);
  const showingResyncModal = useSelector((state) => state.entityPipeline.showingResyncModal);
  const requestingResyncStatus = useSelector((state) => state.entityPipeline.requestingResyncStatus);
  const requestingResyncError = useSelector((state) => state.entityPipeline.requestingResyncError);

  const { entityPipelineFetching } = useEnhancedSelector((state) => state.entityPipeline);

  const resyncDetail = useMemo(() => {
    return resyncDetails[entityPipeline?.targetId];
  }, [resyncDetails, entityPipeline?.targetId]);

  return {
    resyncDetail,
    resyncDetails,
    entityPipeline,
    pipelineHasDraft,
    lastSynctime,
    sourceEntities,
    prevLastSyncTime,
    showingResyncModal,
    requestingResyncStatus,
    requestingResyncError,
    entityPipelineFetching,
  };
}

const ResyncRequestModal = () => {
  const [dateScope, setDateScope] = useState(DateScopes[0]);
  const [dates, setDates] = useState(initialDates);
  const [errors, setErrors] = useState(initialErrors);
  // TODO: type source nodes
  const [selectedSources, setSelectedSources] = useState<any[]>([]);
  const [submitCount, setSubmitCount] = useState(0);
  const moment = useUserLocalMoment();

  const entityMatch = useMatch(routeToMatch(RouteConstants.ENTITY));

  const {
    sourceEntities,
    entityPipeline,
    requestingResyncError,
    resyncDetail,
    requestingResyncStatus,
    showingResyncModal,
    entityPipelineFetching,
  } = useResyncStates();

  const { pipelineIsPaused } = useSyncStatusForEntity(entityPipeline.targetId);

  // Reset when the modal become visible
  useEffect(() => {
    if (showingResyncModal) {
      setSelectedSources([]);
      setSubmitCount(0);
      setErrors(initialErrors);
      setDates(initialDates);
      setDateScope(DateScopes[0]);
    }
  }, [showingResyncModal]);

  const isSubmitting = requestingResyncStatus === FETCH_STATUS.LOADING;
  const dispatch = useDispatch();

  const handleRequestResync = () => {
    setSubmitCount((prev) => prev + 1);

    if (isValid) {
      dispatch(
        resyncEntityPipelineForSources({
          entityId: entityPipeline.targetId,
          sourceEntityIds: selectedSources,
          ...datesToJson(dates),
        })
      ).then(() => {
        // Refetch entities and show toast if we are on the Sync Studio details page
        if (entityMatch) {
          message.success(tn('resync_start_successful'));
          dispatch(getEntities());
        }
      });
    }
  };

  useEffect(() => {
    // reset dates based on scope
    if (dateScope === ALL_RECORDS) {
      setDates(initialDates);
    }
  }, [dateScope]);

  const updateErrors = useDebouncedFn((selectedDates, sources, selectedDateScope) => {
    const hasFromDate = Boolean(selectedDates.fromDate);
    const hasToDate = Boolean(selectedDates.toDate);

    setErrors({
      sources: sources.length < 1,
      fromDate:
        (selectedDateScope === AFTER_DATE && !hasFromDate) || (selectedDateScope === BETWEEN_DATES && !hasFromDate),
      toDate: selectedDateScope === BETWEEN_DATES && !hasToDate,
    });
  }, 100);

  // run validation when data changes
  useEffect(() => {
    updateErrors(dates, selectedSources, dateScope);
  }, [dates, selectedSources, dateScope, updateErrors]);

  const handleSelectionChange = (sourceId: string) => {
    setSelectedSources((prev) => {
      if (prev.includes(sourceId)) {
        return prev.filter((id) => id !== sourceId);
      } else {
        return [...prev, sourceId];
      }
    });
  };

  // toDate is disabled if,
  // date is after now
  // fromDate is set and fromDate is AFTER date
  const toDateDatePredicate = (date?: Moment | null): boolean =>
    !!(date?.isAfter(moment()) || (dates?.fromDate && dates.fromDate.isAfter(date)));

  // fromDate is disabled if,
  // date is after now
  // toDate is set and toDate is BEFORE date
  const fromDateDatePredicate = (date?: Moment | null): boolean =>
    !!(date?.isAfter(moment()) || (dates?.toDate && dates.toDate.isBefore(date)));

  // simple validation to ensure we have sources and the correct dates selected
  const isValid = Object.values(errors).every(invert(Boolean));
  const hasAttemptedSubmission = submitCount > 0;

  const previousScope = useMemo(() => {
    if (moment(resyncDetail?.startTime).unix() <= 0) {
      return tn(`DateScopes.${ALL_RECORDS}`);
    }
    return tn(`DateScopes.after_label`, {
      datetime: moment(resyncDetail?.startTime).format(SHORT_DATE_TIME_FORMAT),
      interpolation: { escapeValue: false },
    });
  }, [resyncDetail, moment]);

  const onRequestClose = () => dispatch(showResyncModal(false));

  return (
    <Modal
      centered
      visible={showingResyncModal}
      title={tn('resync')}
      wrapClassName="resync-modal"
      onCancel={onRequestClose}
      footer={
        <>
          <Button key="cancel" onClick={onRequestClose} aria-label={tc('cancel')}>
            {tc('cancel')}
          </Button>
          <Button
            key="ok"
            aria-label={tn('resync_modal_resync_btn_aria_label')}
            disabled={!isValid || isSubmitting}
            type="primary"
            onClick={handleRequestResync}>
            {tn('resync_modal_resync_btn_title')}
          </Button>
        </>
      }>
      {entityPipelineFetching ? (
        <div className="resync-modal--loading">
          <Spinner />
          <span>{tn('loading_entity_pipeline')}</span>
        </div>
      ) : (
        <Stack>
          {pipelineIsPaused && (
            <div className="entity-resync-error">
              <Alert type="warning" message={tn('resync_on_paused')} description={tn('resync_on_paused_description')} />
            </div>
          )}
          {requestingResyncStatus === FETCH_STATUS.ERROR && (
            <div className="entity-resync-error">
              <Alert type="error" message={tn('resync_start_failed')} description={requestingResyncError} />
            </div>
          )}
          <div>
            {resyncDetail?.lastResyncTime && (
              <>
                <Stack spacing="xs">
                  <Text color="gray-800" weight="semibold">
                    {tn('last_resynced', {
                      datetime: resyncDetail?.lastResyncTime
                        ? moment(resyncDetail?.lastResyncTime).format(SHORT_DATE_TIME_FORMAT)
                        : tc('never'),
                      interpolation: { escapeValue: false },
                    })}
                  </Text>
                  <Text color="gray-800">{previousScope}</Text>
                </Stack>
                <Divider />
              </>
            )}
            <div className="synri-label">{tn('resync_modal_source_entities')}</div>
            <ul className="entity-source-well">
              {/* TODO: Type source nodes */}
              {sourceEntities.map((source: any) => {
                const isChecked = selectedSources.includes(source.configuration.entityDefinition);
                const onChange = handleSelectionChange.bind(null, source.configuration.entityDefinition);
                return (
                  <li
                    key={source.configuration.entityDefinition}
                    className={cx('entity-source-row', isChecked && 'is-checked')}>
                    <Checkbox checked={isChecked} onChange={onChange}>
                      <Tooltip mouseEnterDelay={0.5} title={`${source.name} (${source.subLabel})`}>
                        {`${source.name} (${source.subLabel})`}
                      </Tooltip>
                    </Checkbox>
                  </li>
                );
              })}
            </ul>
          </div>
          <div>
            <div className="synri-label">{tn('resync_modal_scope')}</div>
            <Stack spacing="sm">
              <Select value={dateScope} onChange={setDateScope}>
                {DateScopes.map((scope) => (
                  <Select.Option key={scope} value={scope}>
                    {tn(`DateScopes.${scope}`)}
                  </Select.Option>
                ))}
              </Select>

              {dateScope === AFTER_DATE && (
                <DateTimeSelector
                  key="after"
                  onChange={(fromDate: Moment) => setDates({ fromDate, toDate: undefined })}
                  value={dates.fromDate}
                  disabledDatePredicate={fromDateDatePredicate}
                  hasError={hasAttemptedSubmission && errors?.fromDate}
                />
              )}

              {dateScope === BETWEEN_DATES && [
                <DateTimeSelector
                  key="from"
                  prefix={tn('DatePickers.from')}
                  onChange={(fromDate: Moment) =>
                    setDates((prevDates) => ({
                      ...prevDates,
                      fromDate,
                    }))
                  }
                  value={dates.fromDate}
                  disabledDatePredicate={fromDateDatePredicate}
                  hasError={hasAttemptedSubmission && errors?.fromDate}
                />,
                <DateTimeSelector
                  key="to"
                  prefix={tn('DatePickers.to')}
                  onChange={(toDate: Moment) =>
                    setDates((prevDates) => ({
                      ...prevDates,
                      toDate,
                    }))
                  }
                  value={dates.toDate}
                  disabledDatePredicate={toDateDatePredicate}
                  hasError={hasAttemptedSubmission && errors?.toDate}
                />,
              ]}
            </Stack>
          </div>
        </Stack>
      )}
    </Modal>
  );
};

export default ResyncRequestModal;
