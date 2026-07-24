//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps } from '@reach/router';
import { Button, Form } from 'antd';
import { ChangeEventHandler, FormEventHandler, useState } from 'react';

import { setOauth } from 'actions/specterActions';
import Input from 'components/inputs/Input';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useWindowTitle } from 'hooks/windowTitle';
import { useUserRolesForCurrentInstance } from 'store/user/hooks';
import CapConstants from 'utils/CapConstants';
import { tNamespaced } from 'utils/i18nUtil';

import './Specter.less';

const tn = tNamespaced('Settings.Specter');

// eslint-disable-next-line no-empty-pattern
const ResetSubscription = ({}: RouteComponentProps) => {
  useWindowTitle(tn('page_title'));
  const dispatch = useEnhancedDispatch();
  const enableSpecterDebuggingStatus = useEnhancedSelector((state) => state.specter.enableSpecterDebuggingStatus);
  const [fromSyncariId, setFrom] = useState<string>('');
  const [toSyncariId, setTo] = useState<string>('');

  const { userCan } = useUserRolesForCurrentInstance();
  if (!userCan(CapConstants.SUPER_ADMIN)) {
    navigate('/settings');
  }

  const onChangeFromSyncariId: ChangeEventHandler<HTMLInputElement> = (event) => {
    setFrom(event.target.value);
  };

  const onChangeToSyncariId: ChangeEventHandler<HTMLInputElement> = (event) => {
    setTo(event.target.value);
  };

  const resetSub: FormEventHandler = (e) => {
    e.preventDefault();
    dispatch(setOauth({ fromSyncariId, toSyncariId }));
  };

  const eitherFieldBlank = !toSyncariId || !fromSyncariId;
  return (
    <div className="specter">
      <div className="synri-label">{enableSpecterDebuggingStatus}</div>
      <Form onSubmit={resetSub}>
        <label htmlFor="from-id" className="synri-label">
          {tn('from_id')}
        </label>
        <Input id="from-id" onChange={onChangeFromSyncariId} value={fromSyncariId} />

        <label htmlFor="to-id" className="synri-label">
          {tn('to_id')}
        </label>
        <Input id="to-id" onChange={onChangeToSyncariId} value={toSyncariId} />

        <Button className="login-button" type="primary" htmlType="submit" disabled={eitherFieldBlank}>
          {tn('submit_button')}
        </Button>
      </Form>
    </div>
  );
};

export default ResetSubscription;
