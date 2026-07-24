//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { useEffect, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { useSkullConfigContext } from 'components/skull';
import { FieldMergePolicyContextProvider } from 'pages/sync-studio/entity-pipeline/field-merge-policy-retain-field/FieldMergePolicyContext';
import { humanize } from 'utils/StringUtil';

import ConfigFooter from './ConfigFooter';
import ConfigPage from './ConfigPage';
import ConfigSteps from './ConfigSteps';

import './ConfigWizard.less';

const ConfigWizard = () => {
  const { close, nodeConfig, groupConfiguration, configTitle } = useSkullConfigContext();
  const [title, setTitle] = useState('');

  useEffect(() => {
    // Use raw text if title otherwise humanize the default name
    if (configTitle) {
      setTitle(configTitle);
    } else if (groupConfiguration) {
      setTitle(groupConfiguration.title || humanize(groupConfiguration.name || ''));
    } else if (nodeConfig) {
      setTitle(nodeConfig.displayName || humanize(nodeConfig.name));
    }
  }, [nodeConfig, groupConfiguration, configTitle]);

  return (
    <DrawerPanel
      title={title}
      className="synri-config-wizard"
      width="full"
      keyboard={false}
      maskClosable={false}
      visible
      footer={<ConfigFooter />}
      onClose={close}>
      <ConfigSteps />
      <ScrollableArea bottomOffset={50}>
        <FieldMergePolicyContextProvider>
          <ConfigPage />
        </FieldMergePolicyContextProvider>
      </ScrollableArea>
    </DrawerPanel>
  );
};

export default ConfigWizard;
