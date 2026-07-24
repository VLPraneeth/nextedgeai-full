import { useCallback } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import Spinner from 'components/Spinner';
import { useGetHttpCustomSynapseEntityPaginationQuery } from 'store/custom-synapse/http/api';
import { EntityPaginationItem, EntityPaginationType, HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

const getPaginationInputs = (
  paginationOptions: EntityPaginationItem[] | undefined,
  entity: Partial<HTTPCustomSynapseEntity>,
  onChange: (name: string, value: string) => void,
  readOnly?: boolean
) => {
  const inputs: Array<JSX.Element | undefined> = [];

  paginationOptions?.forEach((option) => {
    if (entity.type === option.name) {
      option.fields.forEach((field) => {
        const { name, dataType, label, helpSummary: tooltip, required, options, visibilityCondition } = field;

        if (visibilityCondition) {
          const { field: conditionField, value: conditionValue } = visibilityCondition;
          if (entity[conditionField] !== conditionValue) {
            return;
          }
        }

        inputs.push(
          <InputWithLabel
            key={name}
            name={name}
            onChange={(arg: any) => {
              if (dataType === 'picklist') {
                onChange(name, arg);
              } else {
                onChange(name, arg.target.value);
              }
            }}
            readOnly={readOnly}
            value={entity?.[name]}
            datatype={dataType}
            tooltip={tooltip}
            required={required}
            label={label}
            optionData={options}
          />
        );
      });
    }
  });
  return inputs;
};

export function EntityPagination({
  entity,
  setEntity,
  readOnly,
}: {
  entity: Partial<HTTPCustomSynapseEntity>;
  setEntity: (entity: Partial<HTTPCustomSynapseEntity>) => void;
  readOnly?: boolean;
}) {
  const { data: paginationOptions, isLoading } = useGetHttpCustomSynapseEntityPaginationQuery();

  const handlePaginationInputsChange = useCallback(
    (name: string, value: string) => {
      setEntity({
        [name]: value,
      });
    },
    [setEntity]
  );

  if (isLoading) {
    return <Spinner />;
  }

  return (
    <Stack>
      <InputWithLabel
        label={tn('pagination_type')}
        value={entity.type}
        onChange={(type: EntityPaginationType) => {
          setEntity({ type });
        }}
        disabled={readOnly}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={paginationOptions?.map((type) => ({
          label: type.displayName,
          value: type.name,
        }))}
      />

      {getPaginationInputs(paginationOptions, entity, handlePaginationInputsChange, readOnly)}
    </Stack>
  );
}
