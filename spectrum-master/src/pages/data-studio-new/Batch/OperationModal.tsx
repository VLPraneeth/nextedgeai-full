import { Suspense } from 'react';

import { withI18n } from 'components/I18nProvider';
import { LeftValue } from 'components/inputs/types';
import RouteSpin from 'components/RouteSpin';
import { EntityFilter } from 'store/data-studio/types';
import { useEntity } from 'store/entity';
import { numberFormat } from 'utils/i18nUtil';
import { EnhancedReactLazy } from 'utils/ModuleUtils';
import { UnreachableCaseError } from 'utils/TypeUtils';

import OperationModalProvider from './OperationModalProvider';
import { BatchOperationMode } from './types';
import './OperationModal.less';

const DeleteModal = EnhancedReactLazy(() => import('./DeleteOperationModal'));
const UpdateModal = EnhancedReactLazy(() => import('./UpdateOperationModal'));

export type OperationModalProps = {
  entityId: string;
  fieldValues: LeftValue[];
  filter?: Partial<EntityFilter>;
  mode: BatchOperationMode;
  onRequestClose: () => void;
  recordsCount?: number;
};

const OperationModal = ({
  entityId,
  fieldValues,
  filter,
  mode,
  onRequestClose,
  recordsCount = 0,
}: OperationModalProps) => {
  const { data: entity, loading } = useEntity(entityId);

  if (loading) {
    // This shouldn't actually be true because this is a modal used
    // in DataStudio which would have already loaded the Entity…but we need to protect against it
    return <RouteSpin />;
  }

  if (!entity) {
    // This should never hit because we've loaded the parent page already with this data
    return null;
  }

  const { displayName: entityName } = entity;
  const count = recordsCount;
  const i18nArgs = { entityName, count, formattedCount: numberFormat(count) };

  const getContent = () => {
    if (mode === BatchOperationMode.DELETE || mode === BatchOperationMode.PURGE) {
      return (
        <DeleteModal commonI18nArgs={i18nArgs} entity={entity} fieldValues={fieldValues} filter={filter} mode={mode} />
      );
    }

    if (mode === BatchOperationMode.UPDATE) {
      return <UpdateModal commonI18nArgs={i18nArgs} entity={entity} fieldValues={fieldValues} filter={filter} />;
    }

    if (mode === BatchOperationMode.NONE) {
      return null;
    }

    throw new UnreachableCaseError(mode);
  };

  return (
    <OperationModalProvider mode={mode} onRequestClose={onRequestClose}>
      <Suspense fallback={<RouteSpin />}>{getContent()}</Suspense>
    </OperationModalProvider>
  );
};

export default withI18n(OperationModal, 'DataStudio.BatchOperation');
