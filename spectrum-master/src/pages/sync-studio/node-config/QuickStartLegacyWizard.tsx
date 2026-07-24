//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import DrawerPanel from 'components/DrawerPanel';
import { useSkullConfigContext } from 'components/skull';

import ConfigFooter from './ConfigFooter';
import ConfigPage from './ConfigPage';
import ConfigSteps from './ConfigSteps';

import './ConfigWizard.less';

const QuickStartWizard = () => {
  const context = useSkullConfigContext();

  return (
    <DrawerPanel
      title={context.configTitle}
      className="synri-config-wizard"
      keyboard={false}
      maskClosable={false}
      width="full"
      visible
      footer={<ConfigFooter />}
      onClose={context.close}>
      <ConfigSteps />
      <ConfigPage className="synri-config-page-quick-start" />
    </DrawerPanel>
  );
};

export default QuickStartWizard;
