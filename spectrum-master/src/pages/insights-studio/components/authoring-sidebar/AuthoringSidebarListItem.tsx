import { navigate } from '@reach/router';
import { message } from 'antd';

import { ReactComponent as RowTable } from 'assets/icons/row-table.svg';
import InlineMessage from 'components/InlineMessage';
import { MenuItem } from 'components/KebabMenu';
import Modal from 'components/Modal';
import { TextTag } from 'components/text-tag';
import useUserLocalMoment from 'hooks/moment';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useUnifiedDataCardNavigate } from 'pages/insights-studio/utils/useUnifiedDataCardNavigate';
import { useDeleteDataCardMutation, useDeleteDatasetMutation } from 'store/insights-studio';
import { DataCard, Dataset } from 'store/insights-studio/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { DraggablePanelItem } from '../draggable-panel-item/DraggablePanelItem';

const tn = tNamespaced('InsightsStudio');

export interface AuthoringSidebarListItemProps {
  item: DataCard | Dataset;
  itemType: 'dataset' | 'datacard';
}

export const AuthoringSidebarListItem = ({ item, itemType }: AuthoringSidebarListItemProps) => {
  const isDataset = itemType === 'dataset';
  const { navigateTo, getCurrentDashboard } = useUnifiedDataCardNavigate();
  const { userHasPermission } = useUserHasPermission();
  const [deleteDataset] = useDeleteDatasetMutation();
  const [deleteDataCard] = useDeleteDataCardMutation();

  const deleteFunc = isDataset ? deleteDataset : deleteDataCard;

  const handleDelete = () => {
    deleteFunc(item.id).then((result) => {
      if ('data' in result) {
        message.success(isDataset ? tn('data_set_deleted') : tn('data_card_deleted'));
      } else {
        message.error(getRtkQueryErrorMessage(result.error));
      }
    });
  };

  const confirmDelete = () => {
    Modal.confirm({
      className: 'authoring-sidebar-list__delete-modal',
      title: isDataset ? tn('data_set_delete') : tn('data_card_delete'),
      content: (
        <>
          <InlineMessage initallyExpanded type="warning">
            <span>
              {item.displayName} ({item.name})
            </span>
          </InlineMessage>
          {tn('delete_confirm')}
        </>
      ),
      onOk: handleDelete,
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
  };

  const openEdit = () => {
    navigateTo(isDataset ? 'DATASET' : 'DATACARD', item.id);
  };

  const openDuplicate = () => {
    const { dashboardId } = getCurrentDashboard();
    navigate(
      isDataset
        ? makeUrl(RouteConstants.INSIGHTS_STUDIO_DATASET_COPY, {
            dashboardId,
            datasetId: item.id,
          })
        : makeUrl(RouteConstants.INSIGHTS_STUDIO_DATA_CARD_COPY, {
            dashboardId,
            dataCardId: item.id,
          })
    );
  };

  const openPreview = () => {
    const { dashboardId } = getCurrentDashboard();
    navigate(
      makeUrl(RouteConstants.INSIGHTS_STUDIO_DATASET_PREVIEW, {
        dashboardId,
        datasetId: item.id,
      })
    );
  };

  const menuItems = item.seeded
    ? []
    : [
        ...(userHasPermission(isDataset ? AllPermissions.UPDATE_DATASET : AllPermissions.UPDATE_DATACARD)
          ? [
              <MenuItem key="edit" onClick={openEdit}>
                {tc('edit')}
              </MenuItem>,
              <MenuItem key="copy" onClick={openDuplicate}>
                {tc('make_copy')}
              </MenuItem>,
            ]
          : []),
        ...(isDataset
          ? [
              <MenuItem key="preview" onClick={openPreview}>
                {tc('preview')}
              </MenuItem>,
            ]
          : []),
        ...(userHasPermission(isDataset ? AllPermissions.DELETE_DATASET : AllPermissions.DELETE_DATACARD)
          ? [
              <MenuItem key="delete" onClick={confirmDelete}>
                {tc('delete')}
              </MenuItem>,
            ]
          : []),
      ];

  const subtitle = item.seeded ? (
    <TextTag text={tc('system')} color="gray" />
  ) : (
    <TextTag text={item.createdBy || tc('user')} color="blue" />
  );

  return (
    <DraggablePanelItem
      key={item.id}
      id={item.id}
      title={item.displayName}
      menuItems={menuItems}
      subtitle={subtitle}
      tooltip={<DetailsTooltip item={item} />}
      disableDrag={!userHasPermission(AllPermissions.UPDATE_DASHBOARD)}
      draggedType={itemType}
      icon={isDataset ? <RowTable /> : undefined}
    />
  );
};

const DetailsTooltip = ({ item }: { item: DataCard | Dataset }) => {
  const moment = useUserLocalMoment();

  const createdDate = item.createdAt ? moment(item.createdAt).format('MMM DD, YYYY h:mm A') : null;
  return (
    <>
      <div style={{ marginBottom: '7px' }}>
        <b>
          {item.displayName} ({item.name})
        </b>
      </div>
      <div style={{ marginBottom: '7px' }}>{item.description}</div>
      <div>Created by: {item.createdBy ?? (item.seeded ? 'System' : 'User')}</div>
      {createdDate && <div>Created at: {createdDate}</div>}
    </>
  );
};
