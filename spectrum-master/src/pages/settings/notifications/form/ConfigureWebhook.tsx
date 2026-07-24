import { Icon } from 'antd';
import { useCallback, useState } from 'react';
import { UnControlled as CodeMirror } from 'react-codemirror2';

import { ReactComponent as BodyIcon } from 'assets/icons/code.svg';
import { ReactComponent as HeadersIcon } from 'assets/icons/headers.svg';
import ActionHeader, { Header } from 'components/custom-action/ActionHeader';
import Fieldset from 'components/Fieldset';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useGetErrorNotificationWebhookBodyQuery } from 'store/error-notifications-v2/api';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { HTTP_URL } from 'utils/RegexUtil';

import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/javascript/javascript';
import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';
import { codeMirrorOptions } from '../utils';

const tn = tNamespaced('Settings.ErrorNotifications');

export function ConfigureWebhook() {
  const [currentTab, setCurrentTab] = useState('headers');
  const {
    errorNotificationFormState: { endpoint, headers },
    setErrorNotificationFormState,
  } = useErrorNotificationContext();
  const { data: body } = useGetErrorNotificationWebhookBodyQuery();
  const [isValidUrl, setIsValidUrl] = useState(true);
  const httpMethods = ['POST', 'PUT', 'PATCH'];
  const httpActionPiclistValues = httpMethods.map((method) => ({ label: method, value: method }));

  const handleEndpointChange = useCallback(
    (selectTextValue: any) => {
      if (selectTextValue.textValue && !HTTP_URL.test(selectTextValue.textValue)) {
        setIsValidUrl(false);
      } else {
        setIsValidUrl(true);
      }
      setErrorNotificationFormState({ endpoint: selectTextValue });
    },
    [setErrorNotificationFormState]
  );

  const handleHeadersChange = useCallback(
    (headers: Header[]) => {
      setErrorNotificationFormState({ headers: headers.map((header) => ({ key: header.key, value: header.value })) });
    },
    [setErrorNotificationFormState]
  );

  return (
    <div>
      <div data-testid="endpoint">
        <InputWithLabel
          required
          label={tc('endpoint')}
          tooltip={tn('notification_endpoint_tooltip')}
          datatype={AppConstants.INPUT_TYPE.SELECT_TEXT}
          value={endpoint}
          selectPicklistValues={httpActionPiclistValues}
          onChange={handleEndpointChange}
          help={!isValidUrl ? tn('invalid_url') : ''}
          validateStatus={!isValidUrl ? 'error' : ''}
        />
      </div>

      <Fieldset
        collapsible
        defaultCollapsed
        showBottomBorder
        className="error-notifications__form--collapse"
        title={tn('webhook_config_header')}>
        <InlineTabs selectedTab={currentTab} onChange={(tab) => setCurrentTab(tab)}>
          <InlineTab id={'headers'}>
            <Icon component={(props) => <HeadersIcon {...props} width={24} height={24} />} />
            <span className="error-notifications__tab-title">{tc('headers')}</span>
          </InlineTab>
          <InlineTab id={'body'}>
            <Icon component={(props) => <BodyIcon {...props} width={24} height={24} />} />
            <span className="error-notifications__tab-title">{tc('body')}</span>
          </InlineTab>
        </InlineTabs>

        {currentTab === 'headers' && <ActionHeader defaultValue={headers} onChange={handleHeadersChange} />}
        {currentTab === 'body' && <CodeMirror value={JSON.stringify(body, null, 4)} options={codeMirrorOptions} />}
      </Fieldset>
    </div>
  );
}
