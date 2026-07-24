import { useNavigate } from '@reach/router';
import * as React from 'react';

import { HStack } from 'components/layout';
import ActionsCell from 'components/renderers/ActionsCellRenderer';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';
import { ReactComponent as ClipboardIcon } from 'assets/icons/clipboard-new.svg';
import { ReactComponent as DataGridEditIcon } from 'assets/icons/pencil-icon-new.svg';
import { ReactComponent as DataGridDeleteIcon } from 'assets/icons/delete-bin-new.svg';

import { useDeleteRecordDataModal } from './hooks';

import './DataStudio.less';
import { IconButton } from 'components/Button';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { tCommon } from 'utils/i18nUtil';

export interface RecordActionCellProps {
  entityId: string;
  recordId: string;
  onRequestRecordDetail: (entityId: string, recordId: string) => void;
  onRecordDeleted?: () => void;
}

export const RecordActionCell = ({
  entityId,
  recordId,
  onRequestRecordDetail,
  onRecordDeleted,
}: RecordActionCellProps) => {
  const navigate = useNavigate();
  const { userHasPermission } = useUserHasPermission();

  const onRequestDelete = useDeleteRecordDataModal(entityId, recordId, {
    onSuccess: onRecordDeleted,
    showToasts: false,
  });

  return (
    <ActionsCell>
      <HStack spacing="xs">
        <IconButton
          className="action-button-icon"
          icon={() => <ClipboardIcon />}
          onClick={() => navigate(makeUrl(RouteConstants.DATA_STUDIO_RECORD_TRANSACTIONS, { entityId, recordId }))}
          title="Lineage"
        />
        <IconButton
          icon={() => <DataGridEditIcon />}
          className={
            userHasPermission(AllPermissions.WRITE_DATA_STUDIO) ? 'action-button-icon' : 'action-button-icon--disabled'
          }
          onClick={(evt: React.SyntheticEvent) => {
            if (!userHasPermission(AllPermissions.WRITE_DATA_STUDIO)) return;
            evt.preventDefault();
            evt.stopPropagation();
            onRequestRecordDetail(entityId, recordId);
          }}
          title={userHasPermission(AllPermissions.WRITE_DATA_STUDIO) ? 'Edit record' : tCommon('permission_error')}
        />
        <IconButton
          className={
            userHasPermission(AllPermissions.WRITE_DATA_STUDIO)
              ? 'action-button-icon delete-icon'
              : 'action-button-icon--disabled delete-icon--disabled'
          }
          icon={() => <DataGridDeleteIcon />}
          onClick={() => {
            if (!userHasPermission(AllPermissions.WRITE_DATA_STUDIO)) return;
            onRequestDelete();
          }}
          title={userHasPermission(AllPermissions.WRITE_DATA_STUDIO) ? 'Delete record' : tCommon('permission_error')}
        />
      </HStack>
    </ActionsCell>
  );
};
