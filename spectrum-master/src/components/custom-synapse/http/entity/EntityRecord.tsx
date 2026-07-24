import { ChangeEvent } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import { tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export function getEntityRecordfields(): {
  name: keyof HTTPCustomSynapseEntity;
  label: string;
  tooltip?: string;
}[] {
  return [
    {
      name: 'recordSelector',
      label: tn('record_selector'),
      tooltip: tn('record_selector_tooltip'),
    },
    {
      name: 'idSelector',
      label: tn('id_selector'),
      tooltip: tn('id_selector_tooltip'),
    },
    {
      name: 'wmSelector',
      label: tn('watermark_selector'),
      tooltip: tn('watermark_selector_tooltip'),
    },
    {
      name: 'createdAtSelector',
      label: tn('created_at_selector'),
      tooltip: tn('created_at_selector_tooltip'),
    },
    {
      name: 'deletedFlagSelector',
      label: tn('deleted_flag_selector'),
      tooltip: tn('deleted_flag_selector_tooltip'),
    },
    {
      name: 'createdBySelector',
      label: tn('created_by_selector'),
      tooltip: tn('created_by_selector_tooltip'),
    },
    {
      name: 'modifiedBySelector',
      label: tn('modified_by_selector'),
      tooltip: tn('modified_by_selector_tooltip'),
    },
  ];
}

export function EntityRecord({
  entity,
  setEntity,
  readOnly,
}: {
  entity: Partial<HTTPCustomSynapseEntity>;
  setEntity: (entity: Partial<HTTPCustomSynapseEntity>) => void;
  readOnly?: boolean;
}) {
  return (
    <Stack>
      {getEntityRecordfields().map((field) => {
        const fieldName = field.name;
        return (
          <InputWithLabel
            label={field.label}
            readOnly={readOnly}
            tooltip={field.tooltip}
            value={entity?.[fieldName]}
            onChange={(newName: ChangeEvent<HTMLInputElement>) => {
              setEntity({ [field.name]: newName.target.value });
            }}
          />
        );
      })}
    </Stack>
  );
}
