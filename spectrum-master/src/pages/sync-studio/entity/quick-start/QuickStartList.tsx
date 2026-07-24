//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Select } from 'antd';
import { useMemo, useState } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import { Stack } from 'components/layout';
import { QuickStart, QuickStarts } from 'store/quick-start/types';

import QuickStartAuthorList from './QuickStartAuthorList';
import QuickStartLibrary from './QuickStartLibrary';

const fullWidthStyle = { width: '100%' };

const Option = Select.Option;

type QuickStartSelectOptions = 'quickStartLibrary' | 'quickStartStudio';

export interface QuickStartListProps {
  setQuickStartAuthorVisible: (quickStart: QuickStart | null) => void;
  setQuickStartInstallVisible: (quickStart: QuickStart) => void;
  authorQuickStarts?: QuickStarts;
}

const QuickStartList = ({ setQuickStartAuthorVisible, setQuickStartInstallVisible }: QuickStartListProps) => {
  const { tn } = useI18nContext();

  const quickStartListTypes = useMemo(() => {
    return [
      {
        label: tn('quickStartLibrary'),
        value: 'quickStartLibrary',
      },
      {
        label: tn('quickStartStudio'),
        value: 'quickStartStudio',
      },
    ] as const;
  }, [tn]);

  const [quickStartListType, setQuickStartListType] = useState<QuickStartSelectOptions>(quickStartListTypes[0].value);

  return (
    <div className="synri-quick-start-panel-container">
      <Stack>
        <Select
          size="large"
          showSearch
          value={quickStartListType}
          onChange={(value: QuickStartSelectOptions) => setQuickStartListType(value)}
          style={fullWidthStyle}>
          {quickStartListTypes.map((qsList) => (
            <Option key={qsList.value} value={qsList.value}>
              {qsList.label}
            </Option>
          ))}
        </Select>
        {quickStartListType === 'quickStartLibrary' && (
          <QuickStartLibrary setQuickStartVisible={setQuickStartInstallVisible} />
        )}
        {quickStartListType === 'quickStartStudio' && (
          <QuickStartAuthorList
            setQuickStartVisible={setQuickStartAuthorVisible}
            setQuickStartInstallVisible={setQuickStartInstallVisible}
          />
        )}
      </Stack>
    </div>
  );
};

export default QuickStartList;
