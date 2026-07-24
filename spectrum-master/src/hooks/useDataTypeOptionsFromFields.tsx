import { uniq, upperFirst } from 'lodash';
import { useMemo } from 'react';

import { FieldItem } from 'components/inputs/FieldOptions';
import { FieldItemProps } from 'components/inputs/FieldOptions/useFieldOptions';
import { Option } from 'components/inputs/Select';
import { sort } from 'utils/ArrayUtil';

const useDataTypeOptionsFromFields = (fields: FieldItemProps[]) => {
  return useMemo(() => {
    const datatypes = sort(uniq(fields.map(({ dataType }) => dataType)));
    return datatypes.map((dataType) => (
      <Option key={dataType} value={dataType}>
        <FieldItem displayName={upperFirst(dataType)} dataType={dataType} apiName="" />
      </Option>
    ));
  }, [fields]);
};

export default useDataTypeOptionsFromFields;
