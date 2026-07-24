//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { Button, Form, Icon, Input, message, Select, Upload } from 'antd';
import { values } from 'lodash';
import { ChangeEvent, ChangeEventHandler, FormEventHandler, useState } from 'react';

import { updateProfile } from 'actions/subscriptionActions';
import Can from 'components/Can';
import { FieldLabelTooltip } from 'components/inputs/FieldGroup';
import { Spacer } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useWindowTitle } from 'hooks/windowTitle';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import { getProfile } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import CapConstants from 'utils/CapConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { humanize } from 'utils/StringUtil';
import useSetState from 'utils/useSetState';

import { PARTNER_MAX_INSTANCE } from './SubscriptionModal';

import './SubscriptionProfile.less';

// 2MB max logo upload size
const MAX_FILE_UPLOAD_SIZE = 2048000;

const tn = tNamespaced('Settings.SubProfile');
const tm = tNamespaced('SubscriptionModal');

const Option = Select.Option;

interface SubscriptionProfileData {
  id: string;
  name: string;
  type: string;
  logo?: File;
  maxInstance?: string;
}

// eslint-disable-next-line no-empty-pattern
const SubscriptionProfile = ({}: RouteComponentProps) => {
  const dispatch = useEnhancedDispatch();

  const orgId = useEnhancedSelector((state) => state.user.orgId);
  const orgName = useEnhancedSelector((state) => state.user.orgName);
  const orgType = useEnhancedSelector((state) => state.user.orgType);
  const maxInstance = useEnhancedSelector((state) => state.user.maxInstance);
  const instanceId = useEnhancedSelector((state) => state.user.currentInstanceNextEdgeId);
  const { userHasPermission } = useUserHasPermission();
  const { userCan } = useUserRolesForCurrentInstance();

  const [subProfileData, setSubProfileData] = useSetState<SubscriptionProfileData>({
    id: orgId,
    name: orgName,
    type: orgType || '',
    logo: undefined,
    maxInstance,
  });

  const [saving, setSaving] = useState(false);
  const [imageKey, setImageKey] = useState(Date.now());

  useWindowTitle(tn('page_title'));

  const _onInputChange: ChangeEventHandler = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setSubProfileData({ [name]: value });
  };

  const save: FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    setSaving(true);
    dispatch(updateProfile(subProfileData)).then(() => {
      dispatch(getProfile());
      if (subProfileData.logo) {
        setImageKey(Date.now());
      }
      setSaving(false);
    });
  };

  return (
    <div className="subscription-profile">
      <Form onSubmit={save}>
        <label className="synri-label" htmlFor="profile-name">
          {tn('subName')}
        </label>
        <Input
          id="profile-name"
          name="name"
          onChange={_onInputChange}
          value={subProfileData.name}
          disabled={!userHasPermission(AllPermissions.SUB_EDIT)}
        />

        <Spacer y="md" />

        <label className="synri-label">{tn('syncariId')}</label>
        <Input value={instanceId} disabled />

        <Spacer y="md" />

        <label className="synri-label" htmlFor="organization-type">
          {tn('org_type')}
        </label>
        {userCan([CapConstants.SUPER_ADMIN]) ? (
          <>
            <Select
              id="organization-type"
              defaultValue={subProfileData.type}
              showSearch
              onChange={(type: string) =>
                setSubProfileData((data) => ({
                  ...data,
                  maxInstance: type === AppConstants.SUBSCRIPTION_TYPES.PARTNER ? PARTNER_MAX_INSTANCE : maxInstance,
                  type,
                }))
              }>
              {values(AppConstants.SUBSCRIPTION_TYPES).map((type) => {
                return (
                  <Option key={type} value={type}>
                    {tm(type)}
                  </Option>
                );
              })}
            </Select>
            {subProfileData.type === AppConstants.SUBSCRIPTION_TYPES.PARTNER && (
              <>
                <Spacer y="md" />
                <label className="synri-label" htmlFor="max-instance">
                  {tn('maximum_instance')}
                </label>
                <Input
                  id="max-instance"
                  name="maxInstance"
                  onChange={_onInputChange}
                  value={subProfileData.maxInstance}
                />
              </>
            )}
          </>
        ) : (
          <Input id="organization-type" value={humanize(subProfileData.type)} disabled />
        )}
        <Spacer y="md" />

        <label className="synri-label" htmlFor="company-logo">
          {tn('image')}
        </label>
        <FieldLabelTooltip icon="question-circle">{tn('company_logo_helper')}</FieldLabelTooltip>

        <div className="logo-upload">
          <img
            id="company-logo"
            key={imageKey}
            width={110}
            height={110}
            src={'/arcade/api/v1/organization/photo'}
            alt="profile"
          />
          <Upload
            accept="image/png, image/jpg, image/jpeg, image/gif"
            fileList={subProfileData.logo ? [subProfileData.logo as any] : []}
            beforeUpload={(file: File) => {
              if (file.size > MAX_FILE_UPLOAD_SIZE) {
                message.warn(tn('file_too_large'));
                return false;
              }

              setSubProfileData({ logo: file });
              return false;
            }}
            onRemove={() => setSubProfileData({ logo: undefined })}
            multiple={false}>
            <Can permission={AllPermissions.SUB_EDIT}>
              <Button>
                <Icon type="picture" />
                {tn('upload_image')}
              </Button>
            </Can>
          </Upload>
        </div>

        <Can permission={AllPermissions.SUB_EDIT}>
          <Button htmlType="submit" key="ok" type="primary" loading={saving}>
            {tn('save_changes')}
          </Button>
        </Can>
      </Form>
    </div>
  );
};

export default SubscriptionProfile;
