import { useMatch } from '@reach/router';

enum SyncStudioMatches {
  ENTITY = '/sync-studio/entity/:entityId/pipeline/:graphVersion',
  ENTITY_VALIDATION = '/sync-studio/entity/:entityId/pipeline/:graphVersion/validation',
  FIELD = '/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion',
  FIELD_VALIDATION = '/sync-studio/entity/:entityId/field/:fieldId/pipeline/:graphVersion/validation',
}

export const useSyncStudioMatch = () => {
  // entity matches
  const entityMatch = useMatch(SyncStudioMatches.ENTITY);
  const entityValidationMatch = useMatch(SyncStudioMatches.ENTITY_VALIDATION);

  // field matches
  const fieldMatch = useMatch(SyncStudioMatches.FIELD);
  const fieldValidationMatch = useMatch(SyncStudioMatches.FIELD_VALIDATION);

  // return the first / only match
  return fieldMatch || fieldValidationMatch || entityMatch || entityValidationMatch;
};
