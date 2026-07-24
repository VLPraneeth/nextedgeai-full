import { useMatch, useNavigate } from '@reach/router';
import { Tooltip } from 'antd';
import Spin from 'antd/lib/spin';
import { useCallback, useState } from 'react';

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

import { SidebarSection, SidebarSectionItem, SidebarSectionLinkItem } from '../Sidebar';
import ReferenceDataRecordKebabMenu from './ReferenceDataRecordKebabMenu';
import ReferenceDataUpsertModal from './ReferenceDataUpsertModal';

const ReferenceDataSidebarSection = () => {
  const navigate = useNavigate();
  const refDataIdMatch = useMatch('/data-studio/referenceData/:refDataId/*');
  const refDataId = refDataIdMatch?.refDataId;

  const { tn } = useI18nContext();
  const { userHasPermission } = useUserHasPermission();

  const [showingCreateModal, setShowingCreateModal] = useState(false);

  const [, { referenceDataId, status: deleteStatus, error: deleteError }] = useDeleteReferenceData();
  const { data: referenceDataList, loading: referenceDataLoading } = useReferenceDataList();

  const onDeleteSuccess = useCallback(() => {
    // if we're currently viewing the Ref Data details page of the
    // Ref Data entity we're deleting, then navigate to DS Root
    if (referenceDataId === refDataId) {
      navigate(RouteConstants.DATA_STUDIO_ROOT);
    }
  }, [navigate, refDataId, referenceDataId]);

  useToastForFetchStatusChange(deleteStatus, { error: deleteError, success: tn('delete_successful') });
  useEffectForValue(deleteStatus, AppConstants.FETCH_STATUS.SUCCESS, onDeleteSuccess);

  return (
    <>
      <SidebarSection
        title={tn('title')}
        callToAction={
          userHasPermission(AllPermissions.WRITE_REFERENCE_DATA) && (
            <button
              type="button"
              className="reference-data-create-call-to-action-btn"
              onClick={() => setShowingCreateModal(true)}>
              +
            </button>
          )
        }>
        <Can permission={AllPermissions.READ_REFERENCE_DATA} errorMode={PermissionErrorModes.ReplaceWithText}>
          {referenceDataList.length > 0 ? (
            referenceDataList.map((refdata) => (
              <SidebarSectionLinkItem
                highlightPartialMatch
                key={refdata.id}
                badge={<NumberText>{refdata.totalRecords}</NumberText>}
                to={makeUrl(RouteConstants.DATA_STUDIO_REFDATA, { refDataId: refdata.id })}
                rightChildren={
                  referenceDataId === refdata.id && deleteStatus === AppConstants.FETCH_STATUS.LOADING ? (
                    <Spin spinning size="small" />
                  ) : (
                    <ReferenceDataRecordKebabMenu referenceData={refdata} />
                  )
                }>
                <Tooltip title={refdata.name}>{refdata.name}</Tooltip>
              </SidebarSectionLinkItem>
            ))
          ) : referenceDataLoading ? (
            <Spin size="small" spinning />
          ) : (
            <SidebarSectionItem>
              <TranslatedText text="no_data" />
            </SidebarSectionItem>
          )}
        </Can>
      </SidebarSection>
      <ReferenceDataUpsertModal onRequestClose={() => setShowingCreateModal(false)} visible={showingCreateModal} />
    </>
  );
};

export default withI18n(ReferenceDataSidebarSection, 'ReferenceDataList');
