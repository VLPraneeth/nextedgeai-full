//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { find, includes, map } from 'lodash';

import { FilteredDropdownItemProps } from 'components/FilteredDropdown/FilteredDropdownItem';
import { Entity } from 'store/entity/types';
import AppConstants from 'utils/AppConstants';
import { navigateTo, NavigateToParams } from 'utils/AppUtil';
import { isCmdOrCtrlPressed } from 'utils/EventHandlerUtil';
import { getDefaultGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { ValuesOf } from 'utils/TypeUtils';
import { replaceToken } from 'utils/UrlUtil';

export const getBreadcrumbMenuItems = (
  type: ValuesOf<typeof AppConstants.LIST_TYPES> | string,
  entities: Entity[],
  paths: string[],
  navigateToParams: NavigateToParams
): FilteredDropdownItemProps[] | null => {
  const mainPage = paths[0];

  // Breadcrumb menus for sync-studio route
  if (includes(RouteConstants.SYNC_STUDIO, mainPage)) {
    const entityId = paths[2];
    const selectedEntity = find(entities, { id: entityId });

    if (type === AppConstants.LIST_TYPES.ENTITY && selectedEntity) {
      const items: FilteredDropdownItemProps[] = map(entities, (entity) => {
        const url = replaceToken(RouteConstants.ENTITY_PIPELINE_GRAPH_VERSION, {
          entityId: entity.id,
          graphVersion: getDefaultGraphVersion(entity.pipelineStatus),
        });
        return {
          id: entity.id,
          to: url,
          title: entity.displayName,
          subtext: entity.apiName,
          onClick: (evt?: React.MouseEvent) => {
            if (isCmdOrCtrlPressed(evt)) {
              return;
            }
            evt?.preventDefault();
            navigateTo(url, navigateToParams);
          },
          selected: selectedEntity.id === entity.id,
        };
      });

      return items;
    }

    if (type === AppConstants.LIST_TYPES.FIELD && selectedEntity) {
      const fieldId = paths[4];

      const items: FilteredDropdownItemProps[] = map(selectedEntity.fields, (field) => {
        const url = replaceToken(RouteConstants.FIELD_PIPELINE, { entityId: selectedEntity.id, fieldId: field.id });
        return {
          id: field.id,
          title: field.displayName,
          subtext: field.apiName,
          to: url,
          onClick: (evt?: React.MouseEvent) => {
            if (isCmdOrCtrlPressed(evt)) {
              return;
            }
            evt?.preventDefault();
            navigateTo(url, navigateToParams);
          },
          selected: fieldId === field.id,
        };
      });

      return items;
    }
  }

  return null;
};
