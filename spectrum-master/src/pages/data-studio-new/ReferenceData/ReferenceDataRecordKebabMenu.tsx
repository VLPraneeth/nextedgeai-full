import { useMatch, useNavigate } from '@reach/router';
import { message } from 'antd';
import { useCallback, useMemo } from 'react';

import Can from 'components/Can';
import { useI18nContext } from 'components/I18nProvider';
import KebabMenu, { KebabMenuClickParams, MenuItem } from 'components/KebabMenu';
import { useUserInputConfirmationModal } from 'hooks/modal';
import useEffectForValue from 'hooks/useEffectForValue';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useDeleteReferenceData, ReferenceDataRecord } from 'store/reference-data';
import AppConstants from 'utils/AppConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { UnreachableCaseError } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import { PanelType } from './ReferenceDataGrid';
import { downloadReferenceData } from './utils';
import { HStack } from 'components/layout';

export enum ReferenceListAction {
  DETAILS = 'Details',
  DOWNLOAD = 'Download',
  UPDATE = 'Update',
  DELETE = 'Delete',
}

const STANDARD_DISABLED_ACTIONS = [ReferenceListAction.DELETE, ReferenceListAction.UPDATE];

export interface ReferenceDataRecordKebabMenuProps {
  referenceData: ReferenceDataRecord;
  actionsToExclude?: ReferenceListAction[];
  onCloseDropdown?: () => void;
}

const ReferenceDataRecordKebabMenu = ({
  referenceData,
  actionsToExclude = [],
  onCloseDropdown,
}: ReferenceDataRecordKebabMenuProps) => {
  const { tc, tn } = useI18nContext();
  const navigate = useNavigate();
  const refDataIdMatch = useMatch('/data-studio/reference-data/:refDataId/*');
  const refDataId = refDataIdMatch?.refDataId;

  const showUserConfirmationModal = useUserInputConfirmationModal();

  const [deleteRefData, { referenceDataId, status: deleteStatus, error: deleteError }] = useDeleteReferenceData();

  const onDeleteSuccess = useCallback(() => {
    // if we're currently viewing the Ref Data details page of the
    // Ref Data entity we're deleting, then navigate to DS Root
    if (referenceDataId === refDataId) {
      navigate(RouteConstants.DATA_STUDIO_ROOT);
    }
  }, [navigate, refDataId, referenceDataId]);

  useToastForFetchStatusChange(deleteStatus, { error: deleteError, success: tn('delete_successful') });
  useEffectForValue(deleteStatus, AppConstants.FETCH_STATUS.SUCCESS, onDeleteSuccess);

  const handleMenuOnClick = useCallback(
    (evt: KebabMenuClickParams<ReferenceListAction>) => {
      switch (evt.key) {
        case ReferenceListAction.DETAILS:
          navigate(
            makeUrl(RouteConstants.DATA_STUDIO_REFDATA, { refDataId: referenceData.id }, { panel: PanelType.DETAILS })
          );
          onCloseDropdown?.();
          break;
        case ReferenceListAction.DELETE:
          onCloseDropdown?.();
          showUserConfirmationModal({
            title: tn('confirm_delete_title', { name: referenceData.name }),
            content: tn('confirm_delete_message', { name: referenceData.name }),
            onOk: () => {
              deleteRefData(referenceData.id);
            },
          });
          break;
        case ReferenceListAction.DOWNLOAD:
          downloadReferenceData(referenceData).catch((err) => {
            message.error(tn('download_failed'));
          });
          onCloseDropdown?.();
          break;
        case ReferenceListAction.UPDATE:
          navigate(
            makeUrl(RouteConstants.DATA_STUDIO_REFDATA, { refDataId: referenceData.id }, { panel: PanelType.UPDATE })
          );
          onCloseDropdown?.();
          break;
        default:
          throw new UnreachableCaseError(evt.key);
      }
    },
    [deleteRefData, navigate, showUserConfirmationModal, tn, referenceData, onCloseDropdown]
  );

  const referenceListMenuItems = useMemo(() => {
    return [
      { key: ReferenceListAction.DETAILS, label: tn('details') },
      { key: ReferenceListAction.DOWNLOAD, label: tc('export') },
      { key: ReferenceListAction.UPDATE, label: tc('update') },
      { key: ReferenceListAction.DELETE, label: tc('delete') },
    ]
      .filter(({ key }) => !actionsToExclude.includes(key))
      .map(({ key, label }) => {
        if (STANDARD_DISABLED_ACTIONS.includes(key)) {
          return referenceData.standard ? (
            <MenuItem key={key} disabled>
              {label}
            </MenuItem>
          ) : (
            <Can key={key} permission={AllPermissions.WRITE_REFERENCE_DATA}>
              <MenuItem>{label}</MenuItem>
            </Can>
          );
        }

        return <MenuItem key={key}>{label}</MenuItem>;
      });
  }, [actionsToExclude, referenceData.standard, tc, tn]);

  return (
    <HStack spacing="xs" className="kebab-menu">
      <KebabMenu<ReferenceListAction> size="small" onClick={handleMenuOnClick} menuItems={referenceListMenuItems} />
    </HStack>
  );
};

export default ReferenceDataRecordKebabMenu;
