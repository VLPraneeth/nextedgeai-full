import RouteSpin from 'components/RouteSpin';
import { useGetDatasetsQuery } from 'store/insights-studio';

import { AuthoringSidebarList } from './AuthoringSidebarList';

export const DatasetPane = () => {
  const { data: datasets, isLoading } = useGetDatasetsQuery();
  if (!datasets || isLoading) {
    return <RouteSpin />;
  }

  return <AuthoringSidebarList list={datasets} listType="dataset" />;
};
