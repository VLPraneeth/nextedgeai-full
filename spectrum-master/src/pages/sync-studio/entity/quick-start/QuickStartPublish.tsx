//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';
import cx from 'classnames';
import { useEffect, useState, useMemo, useCallback } from 'react';

import Can from 'components/Can';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import { showQuickStartPublish } from 'store/entity/thunks';
import {
  useGetAuthorAvailableInstancesQuery,
  usePublishQuickStartMutation,
  useGetQuickStartAuthorListQuery,
} from 'store/quick-start/api';
import { PublishOptions } from 'store/quick-start/types';
import { RoleGroup } from 'utils/CapConstants';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

export interface QuickStartPublishProps {
  className?: string;
  visible: boolean;
  quickStartId: string;
}

const QuickStartPublish = ({ className, visible, quickStartId }: QuickStartPublishProps) => {
  const { data: authorQuickStarts } = useGetQuickStartAuthorListQuery();

  const quickStart = authorQuickStarts?.find((qs) => qs.id === quickStartId);

  const dispatch = useDispatch();
  const { tn } = useI18nContext();
  const { data: instances } = useGetAuthorAvailableInstancesQuery();
  const [shareToOrg, setShareToOrg] = useState(false);
  const [publishToLibrary, setPublishToLibrary] = useState<PublishOptions>();

  const [selectedInstances, setSelectedInstances] = useState<string[]>();
  const [publishQuickStart] = usePublishQuickStartMutation();

  const close = useCallback(() => {
    if (quickStart) {
      dispatch(showQuickStartPublish(false, quickStart.id));
    }
  }, [dispatch, quickStart]);

  const publishToLibraryOptions = useMemo(
    () =>
      [
        {
          label: tn('publish_to_library'),
          value: 'publish',
        },
        {
          label: tn('do_not_publish'),
          value: 'dontPublish',
        },
      ].filter(Boolean),
    [tn]
  );

  useEffect(() => {
    if (visible) {
      setShareToOrg(!!quickStart?.shareWithOrg);
      setPublishToLibrary(
        quickStart?.publishToQuickStartLibrary ? quickStart.publishToQuickStartLibrary : 'dontPublish'
      );
      setSelectedInstances(quickStart?.shareWithInstances ? quickStart.shareWithInstances : []);
    }
  }, [quickStart?.publishToQuickStartLibrary, quickStart?.shareWithInstances, quickStart?.shareWithOrg, visible]);

  const submit = useCallback(async () => {
    if (quickStart) {
      await publishQuickStart({
        instances: selectedInstances,
        publishToLibrary,
        quickStartId: quickStart.id,
        shareToOrg,
      }).unwrap();
      if (quickStart) {
        dispatch(showQuickStartPublish(false, quickStart.id));
      }
    }
  }, [quickStart, publishQuickStart, selectedInstances, publishToLibrary, shareToOrg, dispatch]);

  return (
    <DrawerPanel
      title={tc('publish')}
      className={cx('synri-test-modal', className)}
      onClose={close}
      visible={visible}
      footerClassName={cx('synri-test-modal-footer')}
      footer={
        <>
          <Button onClick={close} className="btn-cancel">
            {tc('cancel')}
          </Button>
          <Button onClick={submit} type="primary">
            {tc('publish')}
          </Button>
        </>
      }>
      <div className="synri-fragment-modal-form-container">
        <Can capability={RoleGroup.SUPER_GHOST}>
          <InputWithLabel
            id="publishToQuickStartLibrary"
            label={tn('publish_to_library_picklist')}
            name="publishToQuickStartLibrary"
            helpSummary={tn('publish_to_library_picklist')}
            datatype="picklist"
            value={publishToLibrary}
            onChange={setPublishToLibrary}
            values={publishToLibraryOptions}
          />
        </Can>
        <Can permission={AllPermissions.QUICKSTART_ORG_SHARE}>
          <InputWithLabel
            datatype="checkbox"
            label={tn('share_with_org')}
            value={shareToOrg}
            name="shareToOrg"
            defaultValue={shareToOrg}
            onChange={(evt: React.ChangeEvent<HTMLInputElement>) => setShareToOrg(evt.target.checked)}
          />
        </Can>
        <Can permission={AllPermissions.QUICKSTART_SHARE}>
          <InputWithLabel
            id="shareWithInstances"
            label={tn('share_with_instance')}
            name="shareWithInstances"
            renderType="instancePicker"
            value={selectedInstances}
            onChange={setSelectedInstances}
            values={instances}
          />
        </Can>
      </div>
    </DrawerPanel>
  );
};

export default withI18n(QuickStartPublish, 'QuickStart');
