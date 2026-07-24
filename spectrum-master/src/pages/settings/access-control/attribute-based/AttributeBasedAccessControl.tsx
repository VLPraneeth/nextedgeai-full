import { useState } from 'react';

import { InlineTab, InlineTabs } from 'components/InlineTabs';
import { HStack, Stack } from 'components/layout';

import { tNamespaced } from 'utils/i18nUtil';
import AttributesTable from './tables/AttributesTable';
import PoliciesTable from './tables/PoliciesTable';
import ValuesTable from './tables/ValuesTable';

import './AttributeBasedAccessControl.scss';

const tn = tNamespaced('Settings.AccessControl.ABAC');

export default function AttributeBasedAccessControl({ path }: { path: string }) {
  const [currentTab, setCurrentTab] = useState('attributes');

  return (
    <Stack spacing="lg" className="attribute-based-access-control">
      <InlineTabs selectedTab={currentTab} onChange={(tab) => setCurrentTab(tab)}>
        <InlineTab id="attributes">
          <HStack align="center">
            <span>{tn('attributes_tab_title')}</span>
          </HStack>
        </InlineTab>

        <InlineTab id="policies">
          <HStack align="center">
            <span>{tn('policies_tab_title')}</span>
          </HStack>
        </InlineTab>

        <InlineTab id="values">
          <HStack align="center">
            <span>{tn('values_tab_title')}</span>
          </HStack>
        </InlineTab>
      </InlineTabs>

      {currentTab === 'policies' && <PoliciesTable />}

      {currentTab === 'attributes' && <AttributesTable />}

      {currentTab === 'values' && <ValuesTable />}
    </Stack>
  );
}
