import { some } from 'lodash';
import { useEffect } from 'react';

import { MetadataDependsOn } from 'components/skull';
import usePreviousValue from 'hooks/usePreviousValue';
import { useGetDependsOnValuesMutation } from 'store/skull/api';

export const useDependsOnData = <T = any>(
  dependsOn?: MetadataDependsOn,
  dependantValues: Record<string, any> = {}
): T => {
  const [getComponentValues, { data: values }] = useGetDependsOnValuesMutation();

  const previousDependantValues = usePreviousValue(dependantValues);

  useEffect(() => {
    if (dependsOn) {
      // The dependantValues change every render cycle and it also includes all
      // form values instead of just the values that this component needs. This
      // check prevents multiple calls to /values which would reset the
      // component data.
      const dependsOnDataChanged = some(
        dependsOn.dependantFields,
        (field) => dependantValues[field] !== previousDependantValues?.[field]
      );
      if (dependsOnDataChanged) {
        getComponentValues({ dependsOn, dependantValues });
      }
    }
  }, [dependantValues, dependsOn, getComponentValues, previousDependantValues]);

  return values;
};
