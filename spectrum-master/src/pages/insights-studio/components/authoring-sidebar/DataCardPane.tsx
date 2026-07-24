import RouteSpin from 'components/RouteSpin';
import { useGetAllDataCardsQuery } from 'store/insights-studio/api';

import { AuthoringSidebarList } from './AuthoringSidebarList';

export const DataCardPane = () => {
  const { data: dataCards, isLoading } = useGetAllDataCardsQuery();

  if (!dataCards || isLoading) {
    return <RouteSpin />;
  }

  return <AuthoringSidebarList list={dataCards} listType="datacard" />;
};
