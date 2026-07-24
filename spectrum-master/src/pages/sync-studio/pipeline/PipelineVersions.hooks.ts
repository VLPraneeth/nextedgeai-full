import { uniq } from 'lodash';
import { useMemo } from 'react';

import useUserLocalMoment from 'hooks/moment';
import { PipelineVersion } from 'store/pipeline/types';

export interface VersionFilterOptionsData {
  versionNumber: string[];
  name: string[];
  createdBy: string[];
  actionType: string[];
  startDate?: string;
  endDate?: string;
}

export const useVersionFilterOptionsData = (versions?: PipelineVersion[]): VersionFilterOptionsData => {
  return useMemo(() => {
    const name: string[] = [];
    const versionNumber: string[] = [];
    const createdBy: string[] = [];
    const actionType: string[] = [];

    versions?.forEach((version) => {
      name.push(version.name);
      versionNumber.push(version.versionNumber.toString());
      createdBy.push(version.createdBy);
      actionType.push(version.actionType);
    });

    return {
      name: uniq(name),
      versionNumber: uniq(versionNumber),
      createdBy: uniq(createdBy),
      actionType: uniq(actionType),
    };
  }, [versions]);
};

const filterArrayKeys = ['versionNumber', 'name', 'createdBy', 'actionType'] as const;

export const useFilteredVersions = (
  filters: VersionFilterOptionsData,
  versions?: PipelineVersion[]
): PipelineVersion[] => {
  const moment = useUserLocalMoment();

  return (
    versions?.filter((version) => {
      const matchesArrayFilters = filterArrayKeys.every((filterKey) => {
        if (filters[filterKey].length) {
          return filters[filterKey].includes(version[filterKey]?.toString());
        }
        return true;
      });

      if (!matchesArrayFilters) {
        return false;
      }

      if (filters.startDate && moment(filters.startDate).isAfter(moment(version.createdAt))) {
        return false;
      }
      if (filters.endDate && moment(filters.endDate).isBefore(moment(version.createdAt))) {
        return false;
      }
      return true;
    }) || []
  );
};
