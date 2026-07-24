//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { Button, Form } from 'antd';
import { FormEventHandler, useEffect, useState } from 'react';

import Can from 'components/Can';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useWindowTitle } from 'hooks/windowTitle';
import { useGetRealtimePipelineIpWhiteQuery, useSetRealtimePipelineIpWhiteMutation } from 'store/realtime-pipeline/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import './RealtimePipelineSettings.scss';

const tRp = tNamespaced('RealtimePipeline');

// eslint-disable-next-line no-empty-pattern
const RealtimePipelineSettings = ({}: RouteComponentProps) => {
  useWindowTitle(tRp('realtime_pipeline_plural'));

  const [saveRealtimeIpWhitelist, { isLoading }] = useSetRealtimePipelineIpWhiteMutation();
  const { data } = useGetRealtimePipelineIpWhiteQuery();

  useEffect(() => {
    if (data?.ipWhitelist) {
      setIpWhitelist(data.ipWhitelist);
    }
  }, [data?.ipWhitelist]);

  const [ipWhitelist, setIpWhitelist] = useState('');
  const [hasChanges, setHasChanges] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const save: FormEventHandler = (e) => {
    e.preventDefault();
    saveRealtimeIpWhitelist({ ipWhitelist })
      .unwrap()
      .then(() => {
        setHasChanges(false);
      })
      .catch((error) => {
        setErrorMessage(getRtkQueryErrorMessage(error));
      });
  };

  return (
    <div className="realtime-pipeline-settings">
      <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
        {errorMessage}
      </InlineMessage>
      <Form onSubmit={save}>
        <InputWithLabel
          label={tRp('ip_whitelist')}
          name="ipWhitelist"
          datatype="textarea"
          rows={10}
          defaultValue={ipWhitelist}
          onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
            setHasChanges(true);
            setIpWhitelist(evt.target.value);
          }}
        />
        <Can permission={[AllPermissions.WRITE_STUDIO]}>
          <Button
            className="realtime-pipeline-settings__save"
            type="primary"
            htmlType="submit"
            loading={isLoading}
            disabled={!hasChanges || isLoading}>
            {tc('save')}
          </Button>
        </Can>
      </Form>
    </div>
  );
};

export default RealtimePipelineSettings;
