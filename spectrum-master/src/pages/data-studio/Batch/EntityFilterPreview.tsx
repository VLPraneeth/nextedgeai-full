import Filter from 'components/inputs/filter';
import { usePicklistValues /* , usePicklistValuesForId */ } from 'store/picklists/hooks';
import AppConstants from 'utils/AppConstants';
import { noop } from 'utils/AppUtil';

import type { OperationModalProps } from './OperationModal';

import './OperationModal.less';

type EntityFilterPreviewProps = Required<Pick<OperationModalProps, 'fieldValues' | 'filter'>>;

function EntityFilterPreview({ fieldValues, filter }: EntityFilterPreviewProps) {
  const [picklistValues, fetchPicklistValues] = usePicklistValues();

  return (
    <div className="entity-filter-preview">
      <Filter
        className="filter-scope-container"
        name={filter.name || 'filter'}
        displayMode={AppConstants.INPUT_DISPLAY_MODE.READONLY}
        fetchPicklistValues={fetchPicklistValues}
        fieldValues={fieldValues}
        onChange={noop}
        onDelete={noop}
        picklistValues={picklistValues}
        value={filter.criteria}
      />
    </div>
  );
}

export default EntityFilterPreview;
