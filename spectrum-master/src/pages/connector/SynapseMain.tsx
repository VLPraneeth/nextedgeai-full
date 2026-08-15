import { RouteComponentProps, Router, navigate } from '@reach/router';
import { useEffect, useState } from 'react';

import { InlineTab, InlineTabs } from 'components/InlineTabs';
import Error404 from 'pages/errors/Error404';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { useEnhancedSelector } from 'hooks/redux';
import { selectUserEmail } from 'store/user/selectors';
import { isGuidedDemoAccount } from 'utils/GuidedDemo';

import ConnectorEditor from './ConnectorEditor';
import { CustomSynapses } from './custom-synapse/CustomSynapses';

import './SynapseMain.scss';

type SynapsesTabs = 'connections' | 'custom-synapses';

const tn = tNamespaced('ConnectorEditor');

export default function SynapseMain({ location, uri }: RouteComponentProps) {
  const [selectedTab, setSelectedTab] = useState<SynapsesTabs>('connections');
  const userEmail = useEnhancedSelector(selectUserEmail);
  const isGuidedDemo = isGuidedDemoAccount(userEmail);
  useEffect(() => {
    if (!location) {
      return;
    }

    if (location.pathname === RouteConstants.SYNAPSES) {
      setSelectedTab('connections');
      navigate(RouteConstants.SYNAPSES_CONNECTIONS);
    } else {
      if (location.pathname === RouteConstants.SYNAPSES_CONNECTIONS) {
        setSelectedTab('connections');
      } else if (location.pathname.startsWith(RouteConstants.SYNAPSES_CUSTOM) && !isGuidedDemo) {
        setSelectedTab('custom-synapses');
      } else if (isGuidedDemo) {
        navigate(RouteConstants.SYNAPSES_CONNECTIONS, { replace: true });
      }
    }
  }, [isGuidedDemo, location]);

  function handleTabChange(tabKey: string) {
    if (!uri) {
      return;
    }
    setSelectedTab(tabKey as SynapsesTabs);
    navigate(`${uri}/${tabKey}`);
  }

  return (
    <>
      <InlineTabs selectedTab={selectedTab} onChange={handleTabChange} className="synapse-page__tabs">
        <InlineTab id="connections">{tn('connections')}</InlineTab>
        {!isGuidedDemo && <InlineTab id="custom-synapses">{tn('custom_synapses')}</InlineTab>}
      </InlineTabs>

      <Router>
        <ConnectorEditor path="/" />
        <ConnectorEditor path="/connections" />
        {!isGuidedDemo && <CustomSynapses path="/custom-synapses/*" />}

        <Error404 default />
      </Router>
    </>
  );
}
