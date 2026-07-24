import { useNavigate } from '@reach/router';
import Icon from 'antd/lib/icon';
import Menu from 'antd/lib/menu';
import Modal from 'antd/lib/modal';
import Spin from 'antd/lib/spin';
import { matchSorter } from 'match-sorter';
import { useCallback, useMemo, useState } from 'react';

import Button, { ButtonProps } from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext } from 'components/I18nProvider';
import KebabMenu from 'components/KebabMenu';
import { HStack, Stack } from 'components/layout';
import SearchBox from 'components/SearchBox';
import { Text } from 'components/typography';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { EntityFilter } from 'store/data-studio';
import { useEntityFiltersList } from 'store/data-studio/hooks';
import { selectFilterBookmarkingStatus, selectFilterIsBookmarked } from 'store/data-studio/selectors';
import { bookmarkEntityFilter, deleteEntityFilter } from 'store/data-studio/thunks';
import AppConstants from 'utils/AppConstants';
import RouteConstants from 'utils/RouteConstants';
import { UnreachableCaseError } from 'utils/TypeUtils';
import { makeUrl } from 'utils/UrlUtil';

import './Filters.less';

enum FilterAction {
  DELETE = 'Delete',
  TOGGLE_FAVE = 'Toggle Fave',
}

interface BookmarkButtonProps extends ButtonProps {
  bookmarked?: boolean;
  filterId: string;
  onClick: () => void;
}

const BookmarkButton = ({ bookmarked = false, filterId, onClick, ...props }: BookmarkButtonProps) => {
  const { tn } = useI18nContext();
  const bookmarkingStatus = useEnhancedSelector((state) => selectFilterBookmarkingStatus(state, filterId));
  const isBookmarked = useEnhancedSelector((state) => selectFilterIsBookmarked(state, filterId));

  useToastForFetchStatusChange(bookmarkingStatus, {
    error: tn('bookmark_filter_error'),
  });

  return (
    <Spin size="small" spinning={bookmarkingStatus === AppConstants.FETCH_STATUS.LOADING}>
      <Button className="filter-bookmark-button" type="link" onClick={onClick} {...props}>
        <Icon type="star" theme={isBookmarked ? 'filled' : 'outlined'} />
      </Button>
    </Spin>
  );
};

interface FilterLineItemProps {
  filter: EntityFilter;
  onRequestApply: (filterId: string) => void;
}

const FilterLineItem = ({ filter, onRequestApply }: FilterLineItemProps) => {
  const { tn } = useI18nContext();
  const dispatch = useEnhancedDispatch();

  const toggleBookmark = useCallback(() => {
    dispatch(bookmarkEntityFilter(filter.id, !filter.bookmarked));
  }, [dispatch, filter]);

  const onFilterDelete = useCallback(() => {
    Modal.confirm({
      title: tn('delete_filter_title'),
      content: tn('delete_filter_content', { name: filter.name }),
      onOk: () => dispatch(deleteEntityFilter(filter.id)),
      okText: tn('delete_filter_confirm_button'),
      okType: 'danger',
    });
  }, [dispatch, filter, tn]);

  return (
    <HStack key={filter.id} className="filter-line-item">
      <BookmarkButton bookmarked={filter.bookmarked} filterId={filter.id} onClick={toggleBookmark} />
      <Text color="black" size="sm">
        {filter.name}
      </Text>
      <HStack className="filter-line-item-actions" spacing="xxs">
        <Button type="default" onClick={() => onRequestApply(filter.id)} size="small">
          <TranslatedText namespace="Common" text="apply" />
        </Button>
        <KebabMenu<FilterAction>
          onClick={({ key }) => {
            switch (key) {
              case FilterAction.DELETE:
                onFilterDelete();
                break;
              case FilterAction.TOGGLE_FAVE:
                toggleBookmark();
                break;
              default:
                throw new UnreachableCaseError(key);
            }
          }}
          menuItems={[
            <Menu.Item key={FilterAction.TOGGLE_FAVE}>
              <TranslatedText text={filter.bookmarked ? 'remove_favorite_filter' : 'add_favorite_filter'} />
            </Menu.Item>,
            <Menu.Item key={FilterAction.DELETE}>
              <TranslatedText text="delete_filter" />
            </Menu.Item>,
          ]}
        />
      </HStack>
    </HStack>
  );
};

// point free helpers
const isBookmarked = (filter: EntityFilter) => filter.bookmarked;
const isNotBookmarked = (filter: EntityFilter) => !isBookmarked(filter);
const sortFiltersByName = (a: EntityFilter, b: EntityFilter) => a.name.localeCompare(b.name);

interface FiltersListDrawerProps {
  entityId: string;
  onRequestClose: () => void;
  visible: boolean;
}

const FiltersListDrawer = ({ entityId, onRequestClose, visible }: FiltersListDrawerProps) => {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const { data, error, loading } = useEntityFiltersList({
    entityId,
    count: 100,
    direction: 'next',
  });

  const handleApplyFilter = useCallback(
    (filterId: string) => {
      navigate(makeUrl(RouteConstants.DATA_STUDIO_ENTITY, { entityId }, { filterId }));
    },
    [entityId, navigate]
  );

  const [bookmarkedFilters, otherFilters] = useMemo(() => {
    if (!data) {
      return [[], []];
    }

    const bookmarkedFilters = data.filters.filter(isBookmarked).sort(sortFiltersByName);
    const otherFilters = data.filters.filter(isNotBookmarked).sort(sortFiltersByName);

    return [bookmarkedFilters, otherFilters];
  }, [data]);

  // naming is hard
  const filteredBookmarkedFilters = matchSorter(bookmarkedFilters, searchQuery, { keys: ['name'] });
  const filteredOtherFilters = matchSorter(otherFilters, searchQuery, { keys: ['name'] });

  const filteredItems = [...filteredBookmarkedFilters, ...filteredOtherFilters];

  return (
    <DrawerPanel
      className="filter-detail-panel"
      title={<TranslatedText text="open_filter" />}
      mask
      onClose={onRequestClose}
      visible={visible}>
      <>
        {loading && <Spin spinning />}
        {error && (
          <Stack>
            <TranslatedText text="error_loading_filters" />
          </Stack>
        )}
        {data && (
          <Stack>
            <SearchBox allowClear value={searchQuery} onChange={(evt) => setSearchQuery(evt.target.value)} />
            <div>
              {filteredItems.length > 0 ? (
                filteredItems.map((filter) => (
                  <FilterLineItem key={filter.id} filter={filter} onRequestApply={handleApplyFilter} />
                ))
              ) : (
                <HStack justify="center">
                  <TranslatedText text="no_filters_found" />
                </HStack>
              )}
            </div>
          </Stack>
        )}
      </>
    </DrawerPanel>
  );
};

export default FiltersListDrawer;
