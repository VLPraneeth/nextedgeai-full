//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as Chemistry } from 'assets/icons/chemistry.svg';
import { ReactComponent as CodeIcon } from 'assets/icons/code.svg';
import { ReactComponent as HeadersIcon } from 'assets/icons/headers.svg';
import { ReactComponent as KeyIcon } from 'assets/icons/key.svg';
import { ReactComponent as WandIcon } from 'assets/icons/wand.svg';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { SelectTextValue } from 'components/inputs/select-text/SelectText';
import { Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import AppConstants from 'utils/AppConstants';

import ActionAuthentication, { ActionAuthenticationValue } from './ActionAuthentication';
import ActionBody from './ActionBody';
import ActionHeader, { Header } from './ActionHeader';
import { ActionTesting, ActionTestingValue } from './ActionTesting';
import ActionVariable, { Variable } from './ActionVariable';
import { ActionSetupValue, ActionTabName, CustomAction } from './types';

import './ActionSetup.less';

const TabIconMap = {
  [ActionTabName.AUTHENTICATION]: KeyIcon,
  [ActionTabName.HEADERS]: HeadersIcon,
  [ActionTabName.BODY]: CodeIcon,
  [ActionTabName.VARIABLES]: WandIcon,
  [ActionTabName.TESTING]: Chemistry,
};

export interface ActionSetupProps {
  className?: string;
  onChange?: (newValue: ActionSetupValue) => void;
  value?: ActionSetupValue;
  defaultValue?: ActionSetupValue;
}

export const ActionSetup = ({ className, value, onChange, defaultValue }: ActionSetupProps) => {
  const [endpoint, setEndpoint] = useState<SelectTextValue>({
    selectValue: defaultValue?.endpoint?.selectValue || '',
    textValue: defaultValue?.endpoint?.textValue || '',
  });
  const [body, setBody] = useState(defaultValue?.body || {});
  const [authentication, setAuthentication] = useState<ActionAuthenticationValue>(defaultValue?.authentication || {});
  const [tab, setTab] = useState<ActionTabName>(ActionTabName.AUTHENTICATION);
  const [headers, setHeaders] = useState<Header[] | undefined>(defaultValue?.headers);
  const [variables, setVariables] = useState<Variable[] | undefined>(defaultValue?.variables);
  const [testingValue, setTestingValue] = useState<ActionTestingValue | undefined>();
  const { tn } = useI18nContext();

  const httpActionPiclistValues = useMemo(
    () => [
      {
        label: tn('GET'),
        value: 'GET',
      },
      {
        label: tn('POST'),
        value: 'POST',
      },
      {
        label: tn('DELETE'),
        value: 'DELETE',
      },
      {
        label: tn('PUT'),
        value: 'PUT',
      },
      {
        label: tn('PATCH'),
        value: 'PATCH',
      },
    ],
    [tn]
  );

  const setSelectedTab = useCallback((tabName: ActionTabName) => {
    if (Object.values(ActionTabName).includes(tabName)) {
      setTab(tabName);
    } else {
      throw new Error(`Invalid tab name ${tabName}`);
    }
  }, []);

  useEffect(() => {
    onChange?.({ endpoint, body, authentication, headers, variables, testingValue });
  }, [endpoint, body, authentication, onChange, headers, variables, testingValue]);

  const setup = useMemo(
    (): CustomAction => ({
      actionConfiguration: {
        authentication,
        body,
        headers,
        variables,
        endpoint,
      },
      displayName: '',
      apiName: '',
    }),
    [authentication, body, endpoint, headers, variables]
  );
  return (
    <Stack className={cx('synri-action-setup', className)} spacing="z">
      <InputWithLabel
        label={tn('endpoint')}
        tooltip={tn('endpoint_tooltip')}
        datatype={AppConstants.INPUT_TYPE.SELECT_TEXT}
        value={endpoint}
        selectPicklistValues={httpActionPiclistValues}
        onChange={setEndpoint}
      />
      <Stack fill className="synri-action-setup-container">
        <InlineTabs selectedTab={tab} onChange={setSelectedTab as any}>
          {Object.values(ActionTabName).map((name) => {
            const Component = TabIconMap[name];
            return (
              <InlineTab id={name} key={name}>
                <span className="synri-action-tab-icon">
                  <Component height={14} width={14} />
                </span>
                <TranslatedText text={name} />
              </InlineTab>
            );
          })}
        </InlineTabs>
      </Stack>

      <Stack fill scrollOverflow>
        {tab === ActionTabName.AUTHENTICATION && (
          <ActionAuthentication defaultValue={authentication} onChange={setAuthentication} />
        )}
        {tab === ActionTabName.HEADERS && <ActionHeader defaultValue={headers} onChange={setHeaders} />}
        {tab === ActionTabName.BODY && <ActionBody defaultValue={body} onChange={setBody} showBatchingInput />}
        {tab === ActionTabName.VARIABLES && <ActionVariable defaultValue={variables} onChange={setVariables} />}
        {tab === ActionTabName.TESTING && (
          <ActionTesting customAction={setup} defaultValue={testingValue} onChange={setTestingValue} />
        )}
      </Stack>
    </Stack>
  );
};

export default withI18n(ActionSetup, 'ActionSetup');
