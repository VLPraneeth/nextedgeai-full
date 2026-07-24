//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Button, Icon } from 'antd';

import { showNodeConfigModal } from 'actions/entityPipelineActions';
import { ListItem, ListItemStatus } from 'components';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { ConfigContext, SkullConfig } from 'components/skull';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetQuickStartsLegacyQuery } from 'store/quick-start-legacy/api';
import { colors, variables } from 'utils/LessConstants';

const warningStyle = { color: colors.red500 };
const rightSpacing = { marginRight: variables.spacings.md };

export interface QuickStartListProps {
  setQuickStartHistoryName: (name: string | null) => void;
}

const QuickStartListLegacy = ({ setQuickStartHistoryName }: QuickStartListProps) => {
  const dispatch = useEnhancedDispatch();
  const { data: quickStarts } = useGetQuickStartsLegacyQuery();

  return (
    <div className="synri-quick-start-container">
      {(quickStarts as SkullConfig[])?.map((quickStart: SkullConfig) => {
        const { requirementsText } = quickStart;
        const disabled = !!requirementsText;
        return (
          <ListItem
            key={quickStart.name}
            status={disabled ? ListItemStatus.disabled : undefined}
            title={quickStart.displayName}
            descriptionTooltip={requirementsText}
            description={
              requirementsText ? (
                <div style={warningStyle}>
                  <Icon type="info-circle" style={rightSpacing} />
                  <TranslatedText text="requirements_not_met" />
                </div>
              ) : (
                quickStart.description
              )
            }
            rightContent={
              <>
                <Button
                  size="small"
                  type="primary"
                  disabled={disabled}
                  onClick={() => {
                    dispatch(showNodeConfigModal(true, ConfigContext.QUICK_START, quickStart.name));
                  }}>
                  <TranslatedText text="start" />
                </Button>
                <KebabMenu
                  menuItems={[
                    <MenuItem key="view_history" onClick={() => setQuickStartHistoryName(quickStart.name)}>
                      <TranslatedText text="view_history" />
                    </MenuItem>,
                  ]}
                />
              </>
            }
          />
        );
      })}
    </div>
  );
};

export default QuickStartListLegacy;
