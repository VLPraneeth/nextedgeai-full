//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { Button, Empty, Radio } from 'antd';
import { sortBy } from 'lodash';
import { useMemo, useState } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import { default as SIcon } from 'components/icons/Icon';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { Stack } from 'components/layout';
import { ListItem } from 'components/list-item';
import SearchBox from 'components/SearchBox';
import TabPanelSpin from 'components/TabPanelSpin';
import { TranslatedText } from 'components/typography';
import useDimensions from 'hooks/useDimensions';
import { EMPTY_ARRAY } from 'store/constants';
import {
  useCancelInstallQuickStartMutation,
  useGetQuickStartMarketplaceListQuery,
  useGetQuickStartSharedListQuery,
} from 'store/quick-start/api';
import { QuickStart, QuickStartInstalls, QuickStartStatus } from 'store/quick-start/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { filterItems } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

export interface QuickStartLibraryProps {
  libraryItems?: QuickStartInstalls;
  sharedItems?: QuickStartInstalls;
  setQuickStartVisible: (quickStart: QuickStart) => void;
}

const QuickStartLibrary = ({ setQuickStartVisible }: QuickStartLibraryProps) => {
  const { tc, tn } = useI18nContext();

  const [cancelInstall] = useCancelInstallQuickStartMutation();
  const { isLoading: isLoadingLibrary, data: libraryQuickStarts } = useGetQuickStartMarketplaceListQuery({});
  const { isLoading: isLoadingShared, data: sharedQuickStarts } = useGetQuickStartSharedListQuery({});
  const [filterString, setFilterString] = useState('');

  const libraryOptions = useMemo(() => {
    return [
      {
        label: tn('library'),
        value: 'library',
      },
      {
        label: tn('sharedWithMe'),
        value: 'sharedWithMe',
      },
    ] as const;
  }, [tn]);

  const [libraryOption, setLibraryOption] = useState<'library' | 'shared'>(() => libraryOptions[0].value);

  const isViewingLibrary = libraryOption === 'library';
  const quickStartItems = (isViewingLibrary ? libraryQuickStarts : sharedQuickStarts) || EMPTY_ARRAY;
  const isLoading = isViewingLibrary ? isLoadingLibrary : isLoadingShared;
  const showEmptyState = !isLoading && (!quickStartItems || !Boolean(quickStartItems.length));

  const emptyStateDescription = isViewingLibrary ? tn('no_public_quick_starts') : tn('no_shared_quick_starts');

  const [measurementRef, dimensions] = useDimensions({ liveMeasure: true });

  const filteredQuickStarts = filterItems(quickStartItems, filterString);

  return (
    <Stack>
      <Radio.Group
        value={libraryOption}
        className="synri-radio-container-flex"
        onChange={(e) => {
          setLibraryOption(e.target.value);
        }}>
        {libraryOptions.map((option) => {
          return (
            <Radio.Button key={option.value} value={option.value} className="synri-radio-option-flex">
              {option.label}
            </Radio.Button>
          );
        })}
      </Radio.Group>

      {showEmptyState && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyStateDescription} />}

      <TabPanelSpin spinning={isLoading} tip={tn('loading_quick_starts')}>
        {!showEmptyState && (
          <SearchBox
            onChange={(event) => setFilterString(event.target.value)}
            placeholder={tc('filter')}
            className="synri-quick-start-library-filter"
          />
        )}
        {!showEmptyState && filterString && !Boolean(filteredQuickStarts.length) && (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tn('no_quick_starts_match_filter')} />
        )}
        <div ref={measurementRef} />
        <Stack className="synri-qs-item-list" style={{ maxHeight: `calc(100vh - ${dimensions.bottom}px)` }}>
          {sortBy(filteredQuickStarts, ['installStatus', 'displayName']).map((item) => {
            const inProgress = item?.installStatus === 'INPROGRESS';
            const description = item.requiredSynapses
              ? tn('required_synapses', { synapses: item.requiredSynapses.join(', ') })
              : '';

            return (
              <ListItem
                key={item.id}
                title={item.displayName}
                titleTooltip={item.displayName}
                description={description}
                descriptionTooltip={description}
                icon={
                  <SIcon
                    className="synri-quick-start-icon"
                    src={makeUrl(DataUrlConstants.QUICK_START_QUICK_START_ICON, {
                      quickStartId: item.id,
                      status: QuickStartStatus.APPROVED,
                    })}
                    alt={item.displayName}
                  />
                }
                rightContent={
                  <>
                    <Button size="small" type="primary" onClick={() => setQuickStartVisible(item)}>
                      {inProgress ? <TranslatedText text="resume" /> : <TranslatedText text="run" />}
                    </Button>
                    {inProgress && (
                      <KebabMenu
                        menuItems={[
                          inProgress && (
                            <MenuItem key="cancel_install" onClick={() => cancelInstall({ quickStartId: item.id })}>
                              <TranslatedText text="cancel_install" />
                            </MenuItem>
                          ),
                          // TODO: Read only view is coming later with SYN-4751
                          // <MenuItem
                          //   key="view_details"
                          //   onClick={() => {
                          //     console.log('show detail panel for quickstart', item.id);
                          //   }}>
                          //   <TranslatedText text="view_details" />
                          // </MenuItem>,
                        ]}
                      />
                    )}
                  </>
                }
              />
            );
          })}
        </Stack>
      </TabPanelSpin>
    </Stack>
  );
};

export default QuickStartLibrary;
