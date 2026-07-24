import { useMatch, useNavigate } from '@reach/router';
import { Tooltip } from 'antd';
import Spin from 'antd/lib/spin';
import { useCallback, useMemo, useState } from 'react';

import Can, { PermissionErrorModes } from 'components/Can';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { TranslatedText } from 'components/typography';
import { NumberText } from 'components/typography';
import useEffectForValue from 'hooks/useEffectForValue';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useDeleteReferenceData, useReferenceDataList } from 'store/reference-data';
import AppConstants from 'utils/AppConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { DataGridDropdownSectionItem, DataGridDropdownSectionLinkItem } from '../DataGridDropdown';
import ReferenceDataRecordKebabMenu from './ReferenceDataRecordKebabMenu';

const ReferenceDataSidebarSection = ({ searchTerm, onSelect }: { searchTerm: string; onSelect: () => void }) => {
  const navigate = useNavigate();
  const refDataIdMatch = useMatch('/data-studio/referenceData/:refDataId/*');
  const refDataId = refDataIdMatch?.refDataId;

  const { tn } = useI18nContext();

  const [, { referenceDataId, status: deleteStatus, error: deleteError }] = useDeleteReferenceData();
  const { data: referenceDataList, loading: referenceDataLoading } = useReferenceDataList();

  // Filter reference data based on search term
  const filteredReferenceData = useMemo(() => {
    if (!searchTerm.trim()) return referenceDataList;
    const term = searchTerm.toLowerCase() || '';
    return referenceDataList?.filter(
      (item) => item.name.toLowerCase().includes(term) || item.key?.toLowerCase().includes(term)
    );
  }, [referenceDataList, searchTerm]);

  const onDeleteSuccess = useCallback(() => {
    // if we're currently viewing the Ref Data details page of the
    // Ref Data entity we're deleting, then navigate to DS Root
    if (referenceDataId === refDataId) {
      navigate(RouteConstants.DATA_STUDIO_ROOT);
    }
  }, [navigate, refDataId, referenceDataId]);

  useToastForFetchStatusChange(deleteStatus, { error: deleteError, success: tn('delete_successful') });
  useEffectForValue(deleteStatus, AppConstants.FETCH_STATUS.SUCCESS, onDeleteSuccess);

  const handleItemClick = useCallback(() => {
    if (onSelect) {
      onSelect();
    }
  }, [onSelect]);

  return (
    <Can permission={AllPermissions.READ_REFERENCE_DATA} errorMode={PermissionErrorModes.ReplaceWithText}>
      {filteredReferenceData?.length > 0 ? (
        filteredReferenceData?.map((refdata) => (
          <DataGridDropdownSectionLinkItem
            highlightPartialMatch
            key={refdata.id}
            badge={<NumberText>{refdata.totalRecords}</NumberText>}
            to={makeUrl(RouteConstants.DATA_STUDIO_REFDATA, { refDataId: refdata.id })}
            onClick={handleItemClick}
            rightChildren={
              referenceDataId === refdata.id && deleteStatus === AppConstants.FETCH_STATUS.LOADING ? (
                <Spin spinning size="small" />
              ) : (
                <ReferenceDataRecordKebabMenu referenceData={refdata} onCloseDropdown={onSelect} />
              )
            }>
            <Tooltip title={refdata.name}>{refdata.name}</Tooltip>
          </DataGridDropdownSectionLinkItem>
        ))
      ) : (
        <DataGridDropdownSectionItem>
          <TranslatedText style={{ padding: '0 12px' }} text={searchTerm ? 'no_matching_reference_data' : 'no_data'} />
        </DataGridDropdownSectionItem>
      )}
    </Can>
  );
};

export default withI18n(ReferenceDataSidebarSection, 'ReferenceDataList');
