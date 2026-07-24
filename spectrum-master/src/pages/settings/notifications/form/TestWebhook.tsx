import { Button, Col, Input, message, Row } from 'antd';
import { useMemo, useState } from 'react';
import { UnControlled as CodeMirror } from 'react-codemirror2';

import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';
import Fieldset from 'components/Fieldset';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import {
  useGetErrorNotificationWebhookBodyQuery,
  usePostErrorNotificationsTestMutation,
} from 'store/error-notifications-v2/api';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { parseJSON } from 'utils/JsonUtil';

import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';
import { codeMirrorOptions } from '../utils';

const tn = tNamespaced('Settings.ErrorNotifications');

export function TestWebhook() {
  const [sendTest, { isLoading, data, error }] = usePostErrorNotificationsTestMutation();
  const {
    errorNotificationFormState: { endpoint, headers },
  } = useErrorNotificationContext();
  const { data: body } = useGetErrorNotificationWebhookBodyQuery();
  const [currentTab, setCurrentTab] = useState('payload');

  const responseValue = useMemo(() => {
    if (error) {
      // Happens when webhook endpoint is invalid
      if ('data' in error && error?.data) {
        return JSON.stringify(error?.data, null, 4);
      }
      return JSON.stringify(error, null, 4);
    }
    // This shows the actual response of the webhook, could be success response or error response
    if (data?.response) {
      return JSON.stringify(
        {
          body: data.response.body && parseJSON(data.response.body),
          statusCodeValue: data.response.statusCodeValue,
          statusCode: data.response.statusCode,
        },
        null,
        4
      );
    }
    return '';
  }, [error, data]);

  return (
    <Fieldset collapsible defaultCollapsed className="error-notifications__form--collapse" title={tn('test_webhook')}>
      <Row gutter={16} justify="space-between">
        <Col span={16}>
          <Input readOnly value={endpoint.textValue} />
        </Col>
        <Col>
          <Button
            loading={isLoading}
            type="primary"
            onClick={() => {
              sendTest({
                type: 'webhook',
                configuration: {
                  body: JSON.stringify(body),
                  headers: headers?.reduce((acc: Record<string, string>, curr) => {
                    const key = curr.key || '';
                    acc[key] = curr.value || '';
                    return acc;
                  }, {}),
                  httpMethod: endpoint.selectValue,
                  url: endpoint.textValue,
                },
              })
                .unwrap()
                .then(() => setCurrentTab('response'))
                .catch(() => {
                  setCurrentTab('response');
                  message.error(tn('test_webhook_error'));
                });
            }}>
            {tc('send_test')}
          </Button>
        </Col>
      </Row>

      <InlineTabs selectedTab={currentTab} onChange={(tab) => setCurrentTab(tab)}>
        <InlineTab id={'payload'}>{tc('payload')}</InlineTab>
        <InlineTab id={'response'}>{tc('response')}</InlineTab>
      </InlineTabs>

      {currentTab === 'payload' && <CodeMirror value={JSON.stringify(body, null, 4)} options={codeMirrorOptions} />}
      {currentTab === 'response' && <CodeMirror value={responseValue} options={codeMirrorOptions} />}
    </Fieldset>
  );
}
