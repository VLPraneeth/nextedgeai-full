//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Col, Input, message, Modal, Row, Spin } from 'antd';
import { CheckboxChangeEvent } from 'antd/lib/checkbox';
import { trim } from 'lodash';
import { useEffect, useState } from 'react';
import { animated, useSpring } from 'react-spring';

import Can from 'components/Can';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Divider, Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { selectAllInstances, selectInstancesLoading } from 'selectors/instanceSelectors';
import { getInstances } from 'store/instances/slice';
import { showInviteUserModal } from 'store/user/actions';
import { getAllRoles, inviteUser } from 'store/user/thunks';
import { UserRoles, UserState } from 'store/user/types';
import { HTTP } from 'utils/AjaxUtil';
import CapConstants from 'utils/CapConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { ApiUserCreds } from './ApiUserCreds/ApiUserCreds';
import UserRoleSelectorInput from './UserRoleSelectorInput';

import './InviteUserModal.less';

const InputGroup = Input.Group;

const tn = tNamespaced('InviteUserModal');

const labelSpan = 5;
const inputSpan = 18;

const validateUserData = ({ firstName, lastName, email, userRoles }: any) => {
  const userValues = [firstName, lastName, email];

  // make sure we have values, and not empty strings
  if (!userValues.map(trim).every(Boolean)) {
    return false;
  }

  // ensure we have roles for each selected instance, and that we
  // have at least 1 instance selected
  const allUserRoles = Object.values(userRoles);
  return allUserRoles.length && allUserRoles.every((items) => (items as any)?.length > 0);
};

interface InviteUserState {
  apiUser: boolean;
  ghostUser: boolean;
  email: string;
  firstName: string;
  inviteUserErrorMessage: string;
  lastName: string;
  userRoles: UserRoles;
}

const initialState: InviteUserState = {
  apiUser: false,
  ghostUser: false,
  email: '',
  firstName: '',
  inviteUserErrorMessage: '',
  lastName: '',
  userRoles: {},
};

const InviteUserModal = () => {
  const { allRoles, sendingInviteUser } = useEnhancedSelector((state) => state.user);
  const orgInstances = useEnhancedSelector(selectAllInstances);
  const orgInstancesLoading = useEnhancedSelector(selectInstancesLoading);

  const [formData, setFormData] = useSetState<InviteUserState>(initialState);
  const [showApiCreds, setShowApiCreds] = useState(false);
  const [newUserData, setNewUserData] = useState<UserState>({
    clientId: '',
    clientSecret: '',
  } as UserState);

  const { inviteUserErrorMessage, userRoles } = formData;

  const dispatch = useEnhancedDispatch();

  // fetch data on mount
  useEffect(() => {
    dispatch(getAllRoles());
    dispatch(getInstances());
  }, [dispatch]);

  const onInputChange = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      [evt.target.name]: evt.target.value,
    });
  };

  const onCheckboxChange = (evt: CheckboxChangeEvent) => {
    const { name, checked } = evt.target;
    // ghost and api user are mutually exclusive
    name &&
      setFormData((prev) => ({
        ...prev,
        [name]: checked,
        apiUser: name === 'ghostUser' && checked ? false : checked,
        ghostUser: name === 'apiUser' && checked ? false : checked,
      }));
  };

  const handleInstanceRoleChange = (instanceId: string, roles?: string[]) => {
    if (typeof roles === 'undefined') {
      let newUserRoles = { ...formData.userRoles };
      delete newUserRoles[instanceId];

      setFormData({ userRoles: newUserRoles });
    } else {
      setFormData({
        userRoles: {
          ...formData.userRoles,
          [instanceId]: roles,
        },
      });
    }
  };

  const close = () => {
    dispatch(showInviteUserModal(false));
  };

  const save = async () => {
    setFormData({ inviteUserErrorMessage: '' });

    const { userRoles, ...userData } = formData;
    const response = await dispatch(inviteUser({ ...userData, admin: false, superAdmin: false, userRoles }));

    if (response.status === HTTP.OK) {
      message.success(tn('successfully_invited_user'));

      // If user is API user, trigger display credentials
      const newUser = response.data;
      if (newUser.isApiUser && newUser.clientId && newUser.clientSecret) {
        setShowApiCreds(true);
        setNewUserData(newUser);
      } else {
        close();
      }
    } else {
      setFormData({ inviteUserErrorMessage: response?.message.message });
    }
  };

  // validate current user input
  const isValid = validateUserData(formData);
  const disableSubmit = !isValid || sendingInviteUser;

  const spring = useSpring(!showApiCreds ? { transform: 'translateX(100%)' } : { transform: 'translateX(0%)' });

  return (
    <Modal
      centered
      className="invite-user-modal"
      footer={
        showApiCreds ? (
          <ApiCredsFooter onClick={close} />
        ) : (
          <InviteUserFooter
            onCancel={close}
            onInvite={save}
            disabled={disableSubmit}
            sendingInviteUser={sendingInviteUser}
          />
        )
      }
      keyboard={false}
      maskClosable={false}
      onCancel={close}
      onOk={close}
      title={tn('title')}
      visible>
      <div className="content-container">
        {showApiCreds ? (
          <animated.div style={spring}>
            <ApiUserCreds clientId={newUserData.clientId} clientSecret={newUserData.clientSecret} />
          </animated.div>
        ) : (
          <>
            {inviteUserErrorMessage ? (
              <InlineMessage title={inviteUserErrorMessage} type={InlineMessageTypes.ERROR}>
                {inviteUserErrorMessage}
              </InlineMessage>
            ) : null}
            <Stack>
              <InputGroup className="sycr-input-group">
                <Row gutter={8}>
                  <Col span={labelSpan}>
                    <label className="synri-label" htmlFor="email">
                      {tn('email')}
                    </label>
                  </Col>
                  <Col span={inputSpan}>
                    <Input id="email" name="email" onChange={onInputChange} required type="email" />
                  </Col>
                </Row>
              </InputGroup>
              <InputGroup className="sycr-input-group">
                <Row gutter={8}>
                  <Col span={labelSpan}>
                    <label className="synri-label" htmlFor="firstName">
                      {tn('first_name')}
                    </label>
                  </Col>
                  <Col span={inputSpan}>
                    <Input id="firstName" name="firstName" onChange={onInputChange} required />
                  </Col>
                </Row>
              </InputGroup>
              <InputGroup className="sycr-input-group">
                <Row gutter={8}>
                  <Col span={labelSpan}>
                    <label className="synri-label" htmlFor="lastName">
                      {tn('last_name')}
                    </label>
                  </Col>
                  <Col span={inputSpan}>
                    <Input id="lastName" name="lastName" onChange={onInputChange} required />
                  </Col>
                </Row>
              </InputGroup>
              <div className="invite-user-modal__user-type">
                <TranslatedText namespace="InviteUserModal" text="user_type" beDangerous />
                <div className="invite-user-modal__user-type__options">
                  <InputWithLabel
                    datatype="checkbox"
                    label={tn('api_user')}
                    name="apiUser"
                    checked={formData['apiUser']}
                    onChange={onCheckboxChange}
                  />
                  <Can key="ghostUser" capability={[CapConstants.SUPER_ADMIN]}>
                    <InputWithLabel
                      datatype="checkbox"
                      label={tn('ghost_user')}
                      name="ghostUser"
                      checked={formData['ghostUser']}
                      tooltip={tn('ghost_tooltip')}
                      onChange={onCheckboxChange}
                    />
                  </Can>
                </div>
              </div>
            </Stack>

            <Divider y="xxl" />

            <div className="synri-label">{tn('instance_permissions')}</div>
            <ul className="instance-permissions-well">
              <Spin spinning={orgInstancesLoading}>
                {orgInstances?.map((instance) => (
                  <UserRoleSelectorInput
                    key={instance.syncariId}
                    availableRoles={allRoles}
                    checked={Boolean(userRoles?.[instance.syncariId])}
                    instance={instance}
                    onChange={handleInstanceRoleChange}
                    roles={userRoles[instance.syncariId]}
                  />
                ))}
              </Spin>
            </ul>
          </>
        )}
      </div>
    </Modal>
  );
};

export default InviteUserModal;

interface InviteUserFooterProps {
  disabled: boolean;
  onCancel: () => void;
  onInvite: () => void;
  sendingInviteUser: boolean;
}

const InviteUserFooter = ({ onCancel, onInvite, disabled, sendingInviteUser }: InviteUserFooterProps) => (
  <>
    <Button key="cancel" onClick={onCancel}>
      {tc('cancel')}
    </Button>
    <Button disabled={disabled} key="ok" onClick={onInvite} type="primary">
      {sendingInviteUser ? tn('sending_invite') : tn('send_invite')}
    </Button>
  </>
);

const ApiCredsFooter = ({ onClick }: { onClick: () => void }) => (
  <Button key="done" onClick={onClick}>
    {tn('done')}
  </Button>
);
