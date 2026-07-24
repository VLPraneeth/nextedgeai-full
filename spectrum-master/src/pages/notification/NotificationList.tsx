//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, WindowLocation } from '@reach/router';
import { Button, DatePicker, Empty, Icon } from 'antd';
import { RangePickerValue } from 'antd/lib/date-picker/interface';
import cx from 'classnames';
import { keyBy } from 'lodash';
import moment from 'moment';
import { useCallback, useEffect, useMemo, useReducer } from 'react';

import { ReactComponent as ArchiveIcon } from 'assets/icons/archive.svg';
import { ReactComponent as Checkmark } from 'assets/icons/mark-as-read-currentcolor.svg';
import { ReactComponent as Bell } from 'assets/icons/mark-as-unread-currentcolor.svg';
import ExpandedRowTriangleIcon from 'components/icons/ExpandedRowTriangleIcon';
import Select from 'components/inputs/Select';
import NotificationErrorState from 'components/notifications/NotificationErrorState';
import NotificationZeroState from 'components/notifications/NotificationZeroState';
import Table from 'components/Table';
import TableRowExpandArrow from 'components/TableRowExpandArrow';
import { useLayoutContext } from 'pages/LayoutContext';
import {
  useArchiveAllNotificationsMutation,
  useArchiveNotificationsMutation,
  useGetNotificationsQuery,
  useGetUnreadNotificationCountQuery,
  useMarkAllNotificationReadMutation,
  useMarkNotificationsReadMutation,
  useMarkNotificationsUnreadMutation,
} from 'store/notifications/api';
import { Notification, NotificationTypes } from 'store/notifications/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { ValuesOf } from 'utils/TypeUtils';

import NotificationActionsRenderer from './NotificationActionsRenderer';
import NotificationReadRenderer from './NotificationReadRenderer';
import NotificationSubjectRenderer from './NotificationSubjectRenderer';
import TableFilterBar from './TableFilterBar';

import './Notification.less';

const TABLE_FILTER_HEIGHT = 46;
const TABLE_HEADER_HEIGHT = 62;
const PAGINATION_HEIGHT = 64;

const { RENDERER } = AppConstants;

const { RangePicker } = DatePicker;

const t = tNamespaced('NotificationList');
const tNotificationTypes = tNamespaced('NotificationTypes');

/*
 * custom render fn for notification expansion
 */
function expandedRowRender(record: { body: any }) {
  return (
    <div className="notifications-table-expanded-row">
      <ExpandedRowTriangleIcon />
      <p style={{ margin: 0 }}>{record && record.body}</p>
    </div>
  );
}

// default date format for the US locale, this should probably
// be dynamic - App context?
const UsDateFormat = 'MM/DD/YYYY';

interface NotificationListState {
  filters: {
    isArchived: boolean;
    dateRange: RangePickerValue;
    type: NotificationTypes;
  };
  expandedRows: string[];
  selectedRows: string[];
}

const getInitialState = (): NotificationListState => {
  return {
    filters: {
      isArchived: false,
      dateRange: [] as any,
      type: NotificationTypes.ALL,
    },
    expandedRows: [],
    selectedRows: [],
  };
};

const CLEAR_ROW_SELECTION = 'ROWS/RESET';
const ROW_SELECTION = 'ROWS/SELECT';
const UPDATE_FILTER = 'FILTERS/UPDATE';
const CLEAR_FILTERS = 'FILTERS/CLEAR';
const EXPAND_ROW = 'ROW/EXPAND';
const COLLAPSE_ROW = 'ROW/COLLAPSE';

const reducer = (state: NotificationListState, action: any): NotificationListState => {
  switch (action.type) {
    // called when antd Table row selection has changed
    case ROW_SELECTION:
      return {
        ...state,
        selectedRows: action.payload,
      };
    // used to clear the table row selection manually
    case CLEAR_ROW_SELECTION:
      return {
        ...state,
        selectedRows: [],
      };
    // a table filter value has changed
    case UPDATE_FILTER: {
      // payload is of shape { key, value }
      // use this to update our filters
      const { key, value } = action.payload;

      return {
        ...state,
        filters: {
          ...state.filters,
          [key]: value,
        },
      };
    }
    // clear all table filters
    case CLEAR_FILTERS:
      return {
        ...state,
        filters: { ...getInitialState().filters },
      };
    case EXPAND_ROW:
      return {
        ...state,
        expandedRows: [...state.expandedRows, action.payload],
      };
    case COLLAPSE_ROW:
      return {
        ...state,
        expandedRows: state.expandedRows.filter((rowId) => action.payload !== rowId),
      };
    default:
      return state;
  }
};

/*
 * build columns for table using custom renderers and i18n helper
 */
function getColumns() {
  return [
    {
      title: t('subject'),
      key: 'subject',
      dataIndex: 'subject',
      render: (text: string, record: Notification, index: number) => (
        <NotificationSubjectRenderer {...{ text, record, index }} />
      ),
    },
    {
      title: t('date_time'),
      key: 'receivedOn',
      dataIndex: 'createdAt',
      renderer: RENDERER.DATE_TIME,
    },
    {
      title: t('status'),
      key: 'read',
      dataIndex: 'read',
      render: (text: string, record: Notification, index: number) => (
        <NotificationReadRenderer {...{ text, record, index }} />
      ),
    },
    {
      key: 'actions',
      render: (text: string, record: Notification, index: number) => (
        <NotificationActionsRenderer {...{ text, record, index }} />
      ),
    },
  ];
}

/*
 * build optionData for Select component, using i18n helper
 */
function getNotificationTypeOptions() {
  return ['all', 'info', 'warn', 'error', 'announcement'].map((type) => ({
    value: type.toUpperCase(),
    label: tNotificationTypes(type),
  }));
}

// some :: Func -> List x -> Boolean
const some = (predicate: (record: Notification) => boolean) => (xs: Notification[]) => xs?.some(predicate);

/**
 * predicate for notification read status
 * @param {Object} notification
 * @return {Boolean}
 */
const isUnread = (notification?: Notification) => !notification?.read;

/**
 * predicate for notification createdAt date between startDate/endDate
 * @param {Moment} startDate - moment from antd RangePicker
 * @param {Moment} endDate - moment from antd RangePicker
 * @return {Boolean}
 */
const isInDateRange = (startDate: moment.Moment, endDate: moment.Moment) => (notification: Notification) =>
  moment(notification.createdAt).isBetween(startDate, endDate);

export interface NotificationListProps {
  location: WindowLocation<{ notificationId?: string }>;
  markAsReadMutation: any;
}

const NotificationList = ({ location: routerLocation }: NotificationListProps) => {
  // if the notificaitonlist was opened from a direct notification link,
  // then we'll init our expandedRows list with that item

  // local state for table filters/actions
  const [state, dispatch] = useReducer(reducer, null, getInitialState);
  const { filters, expandedRows, selectedRows } = state;
  const { data: unreadCount } = useGetUnreadNotificationCountQuery();

  const [markAsReadMutation, { isLoading: isMarkingRead }] = useMarkNotificationsReadMutation();
  const [markAsUnreadMutation, { isLoading: isMarkingUnread }] = useMarkNotificationsUnreadMutation();
  const [archiveMutation, { isLoading: isArchiving }] = useArchiveNotificationsMutation();
  const [markAllAsReadMutation, { isLoading: isMarkingAllRead }] = useMarkAllNotificationReadMutation();
  const [archiveAllMutation, { isLoading: isArchivingAll }] = useArchiveAllNotificationsMutation();

  const isMarkingNotifications = isMarkingRead || isMarkingUnread || isMarkingAllRead;
  const isArchivingNotifications = isArchiving || isArchivingAll;

  // Expand the row when
  useEffect(() => {
    if (routerLocation?.state?.notificationId) {
      dispatch({ type: EXPAND_ROW, payload: routerLocation.state.notificationId });

      // We have to manually reset the state to remove notificationId from the
      // router state so it doesn't stay expanded on subsequent page loads
      navigate('/notifications', { state: {}, replace: true });
    }
  }, [markAsReadMutation, routerLocation?.state?.notificationId]);

  const {
    data: allNotifications,
    isLoading: isLoadingNotifications,
    isError: fetchingError,
  } = useGetNotificationsQuery({
    type: filters.type,
  });

  const allNotificationsById = useMemo(() => {
    return keyBy(allNotifications, 'id');
  }, [allNotifications]);

  const getNotificationsForIds = useCallback(
    (ids: string[]) => ids.map((id) => allNotificationsById[id]).filter(Boolean),
    [allNotificationsById]
  );

  const layoutContext = useLayoutContext();
  const tableScroll = useMemo(
    () => ({
      y:
        layoutContext.dimensions.content.height -
        layoutContext.constants.CONTENT_PADDING -
        TABLE_FILTER_HEIGHT -
        TABLE_HEADER_HEIGHT -
        PAGINATION_HEIGHT,
    }),
    [layoutContext]
  );

  const hasDateFilterApplied = filters.dateRange.some(Boolean);
  // if we have filters applied, this will be true
  const hasFiltersApplied = hasDateFilterApplied;

  // do we have any notification actions in flight?
  const notificationActionInProgress = isMarkingNotifications || isArchivingNotifications;

  // memoize these because of i18n calls
  const columns = useMemo(getColumns, []);
  const NotificationTypeOptions = useMemo(getNotificationTypeOptions, []);

  const [start, end] = filters.dateRange;
  const dateFilterFunction = start && end && isInDateRange(start, end);

  const notifications = allNotifications?.length
    ? hasDateFilterApplied && dateFilterFunction
      ? allNotifications.filter(dateFilterFunction)
      : allNotifications
    : [];

  // called by antd table when a row is expanded or collapsed
  const handleRowExpand = (expanded: boolean, { id }: Notification) => {
    if (expanded) {
      // Mark the notification as read
      markAsReadMutation([id]);
    }

    dispatch({
      type: expanded ? EXPAND_ROW : COLLAPSE_ROW,
      payload: id,
    });
  };

  // this is an antd object spec to enable Table row selection,
  const rowSelection = {
    // props for the checkbox item added to our row
    getCheckboxProps: (record: Notification) => ({ disabled: false, name: record.id }),
    // onChange is fired with complete array of selected rows, not incremental
    onChange: (rowIds: string[]) => dispatch({ type: ROW_SELECTION, payload: rowIds }),
    // use our local state to specify rows that are selected, this will
    // allow us to filter/control/clear selection if needed
    selectedRowKeys: selectedRows,
  };

  // wrapper around dispatch, takes a key and returns a handler
  // that takes value, and then updates the filter in state
  const handleFilterUpdate = (key: keyof NotificationListState['filters']) => (
    value: ValuesOf<NotificationListState['filters']>
  ) =>
    dispatch({
      type: UPDATE_FILTER,
      payload: {
        key,
        value,
      },
    });

  const removeFromSelected = (notificationsToRemove: string[] = []) =>
    dispatch({
      type: ROW_SELECTION,
      payload: selectedRows.filter((selectedRow) => !notificationsToRemove.includes(selectedRow)),
    });
  const clearSelectedRows = () => dispatch({ type: ROW_SELECTION, payload: [] });
  const markSelectedAsRead = () => markAsReadMutation(selectedRows).then(() => removeFromSelected(selectedRows));
  const markSelectedAsUnread = () => markAsUnreadMutation(selectedRows).then(() => removeFromSelected(selectedRows));
  const archiveSelected = () => archiveMutation(selectedRows).then(() => removeFromSelected(selectedRows));
  const archiveAll = () => archiveAllMutation().then(clearSelectedRows);
  const markAllAsRead = () => markAllAsReadMutation().then(clearSelectedRows);

  // if any notifications are unread, then we're going to mark as read. if ALL are read
  // then we'll mark as unread
  const shouldMarkSelectedAsRead =
    allNotifications &&
    allNotifications.length > 0 &&
    selectedRows.length > 0 &&
    some(isUnread)(getNotificationsForIds(selectedRows.filter(Boolean)));

  // clear all active filters
  const onClearFilters = () => dispatch({ type: CLEAR_FILTERS });

  // if we have filters applied, then show an Empty state component
  // that has a CTA that allows clearing all current filters in order
  // to see the whole list.
  //
  // If we don't have any filters, then show our default NotificationZeroState component
  const EmptyStateComponent = fetchingError ? (
    <NotificationErrorState />
  ) : hasFiltersApplied ? (
    <Empty description={t('no_notifications_match_filters')}>
      <Button
        type="primary"
        htmlType="button"
        role="button"
        aria-label={t('clear_all_filters_button_aria_label')}
        onClick={onClearFilters}>
        {t('clear_all_filters_call_to_action_button')}
      </Button>
    </Empty>
  ) : (
    <NotificationZeroState />
  );

  const hasUnreads = unreadCount && unreadCount > 0;

  return (
    <>
      <TableFilterBar>
        <TableFilterBar.Filters>
          <>
            <label htmlFor="filter-type">{t('type_filter_label')}</label>
            <Select
              className="filter-notification-type"
              value={filters.type}
              optionData={NotificationTypeOptions}
              onChange={handleFilterUpdate('type')}
            />
          </>

          <>
            <label htmlFor="filter-date-range">{t('date_filter_label')}</label>
            <RangePicker
              name="filter-date-range"
              allowClear
              separator="&#10095;"
              format={UsDateFormat}
              value={filters.dateRange}
              onChange={handleFilterUpdate('dateRange')}
            />
          </>
          {hasFiltersApplied && (
            <Button
              type="link"
              htmlType="button"
              aria-label={t('clear_all_filters_button_aria_label')}
              role="button"
              onClick={onClearFilters}>
              {t('clear_all_filters_button')}
            </Button>
          )}
        </TableFilterBar.Filters>

        {selectedRows?.length > 0 ? (
          <TableFilterBar.BulkActions>
            {shouldMarkSelectedAsRead ? (
              <Button
                type="primary"
                aria-label={t('mark_selected_as_read_aria_label')}
                role="button"
                onClick={markSelectedAsRead}
                loading={isMarkingNotifications}
                disabled={notificationActionInProgress}>
                <Icon component={Checkmark as any} />
                {t('mark_selected_as_read')}
              </Button>
            ) : (
              <Button
                type="primary"
                aria-label={t('mark_selected_as_unread_aria_label')}
                role="button"
                onClick={markSelectedAsUnread}
                loading={isMarkingNotifications}
                disabled={notificationActionInProgress}>
                <Icon component={Bell as any} />
                {t('mark_selected_as_unread')}
              </Button>
            )}
            <Button
              type="primary"
              aria-label={t('archive_selected_aria_label')}
              role="button"
              onClick={archiveSelected}
              loading={isArchivingNotifications}
              disabled={notificationActionInProgress}>
              <Icon component={ArchiveIcon as any} />
              {t('archive_selected')}
            </Button>
          </TableFilterBar.BulkActions>
        ) : hasUnreads || allNotifications?.length ? (
          <TableFilterBar.BulkActions>
            <Button
              aria-label={t('mark_all_as_read_aria_label')}
              role="button"
              onClick={markAllAsRead}
              loading={isMarkingNotifications}
              disabled={hasUnreads || notificationActionInProgress}>
              <Icon component={Checkmark as any} />
              {t('mark_all_as_read')}
            </Button>
            <Button
              aria-label={t('archive_all_aria_label')}
              role="button"
              onClick={archiveAll}
              loading={isArchivingNotifications}
              disabled={notificationActionInProgress}>
              <Icon component={ArchiveIcon as any} />
              {t('archive_all')}
            </Button>
          </TableFilterBar.BulkActions>
        ) : null}
      </TableFilterBar>

      <Table
        columns={columns}
        className="notifications-table"
        rowClassName={(n: Notification) =>
          cx('notifications-table-row', {
            'is-read': n.read,
            'is-unread': !n.read,
          })
        }
        rowKey="id"
        rowSelection={rowSelection}
        expandedRowRender={expandedRowRender}
        defaultExpandAllRows={false}
        expandIcon={(props: any) => <TableRowExpandArrow {...props} />}
        expandIconAsCell={false}
        expandIconColumnIndex={4}
        expandedRowKeys={expandedRows}
        onExpand={handleRowExpand}
        dataSource={notifications}
        loading={isLoadingNotifications}
        scroll={tableScroll}
        locale={{
          emptyText: EmptyStateComponent,
        }}
      />
    </>
  );
};

export default NotificationList;
