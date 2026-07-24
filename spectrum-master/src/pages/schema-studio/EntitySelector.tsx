import cx from 'classnames';
import { useMemo, useEffect } from 'react';
import * as React from 'react';

import SelectInput from 'components/SelectInput';
import { createUniqueEntityTitle } from 'utils/FieldUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { SchemaVersion, VersionedSchemaData, ConnectorSchema } from './types';

import './EntitySelector.less';

interface EntitySelectorProps {
  entities: Record<string, VersionedSchemaData<ConnectorSchema>>;
  currentEntity: VersionedSchemaData<ConnectorSchema>;
  currentEntityId: string;
  currentVersion: SchemaVersion;
  onChange: (entityId: string, version: SchemaVersion) => void;
}

const VersionTypes = {
  draft: 'Draft',
  published: 'Published',
};

const getAlternateVersion = (version: SchemaVersion): SchemaVersion =>
  version === 'published' ? 'draft' : 'published';

const getDisplayName = (entity: VersionedSchemaData<ConnectorSchema>) =>
  entity.published?.fields.displayName || entity.draft?.fields.displayName || '';

const EntitySelector = ({
  entities,
  currentEntityId,
  currentEntity,
  currentVersion,
  onChange,
}: EntitySelectorProps) => {
  const entityOptions = useMemo(
    () =>
      Object.entries(entities).map(([apiName, entity]) => {
        return {
          value: apiName,
          label: createUniqueEntityTitle(getDisplayName(entity), entity.apiName),
        };
      }),
    [entities]
  );

  useEffect(() => {
    // load the alternate version in the case the requested one is missing
    if (!currentEntityId && currentEntity) {
      const alternateVersion = getAlternateVersion(currentVersion);
      currentEntity[alternateVersion] && onChange(currentEntity.apiName, alternateVersion);
    }
  }, [currentEntityId, currentEntity, onChange, currentVersion]);

  const versionOptions = currentEntity
    ? Object.entries(currentEntity)
        .filter(([, schema]) => schema && typeof schema === 'object')
        .map(([key]) => ({
          value: key,
          label: VersionTypes[key as SchemaVersion],
        }))
    : [];

  const hasVersions = versionOptions.find((version) => version.value === 'draft') || versionOptions.length > 1;

  return (
    <div className="entity-selector" data-userflow-tag={UserflowTags.SchemaStudio.EntitySelector}>
      <SelectInput
        className={cx('entity-select-control', { 'has-versions': hasVersions })}
        options={entityOptions}
        value={currentEntity.apiName}
        showSearch
        onChange={(value) => onChange(value as string, currentVersion)}
      />
      {hasVersions && (
        <SelectInput
          className="version-select-control"
          options={versionOptions}
          value={currentVersion}
          onChange={(value) => onChange(currentEntity.apiName, value as SchemaVersion)}
        />
      )}
    </div>
  );
};

export default EntitySelector;
