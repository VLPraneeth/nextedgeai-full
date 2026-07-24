import TextArea from 'antd/lib/input/TextArea';
import { SelectValue } from 'antd/lib/select';
import { Dispatch, SetStateAction } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import Tag from 'components/inputs/Tag';
import { Stack } from 'components/layout';
import Text from 'components/typography/Text';
import { tNamespaced } from 'utils/i18nUtil';

export interface FirstStepFormProps {
  roleName: string;
  roleDescription: string;
  tags: string[] | [];
  status: SelectValue | undefined;
  setRoleName: Dispatch<SetStateAction<string>>;
  setRoleDescription: Dispatch<SetStateAction<string>>;
  setTags: Dispatch<SetStateAction<string[] | []>>;
  setStatus: Dispatch<SetStateAction<SelectValue>>;
}

const FirstStepForm = ({
  roleName,
  roleDescription,
  tags,
  status,
  setRoleName,
  setRoleDescription,
  setTags,
  setStatus,
}: FirstStepFormProps) => {
  const tn = tNamespaced('Settings.AccessControl.Forms');
  return (
    <Stack className="add-role__form-container">
      <InputWithLabel
        label={tn('role_name')}
        required
        value={roleName}
        onChange={(e: any) => setRoleName(e.target.value)}
        textArea
      />
      <InputWithLabel
        label={tn('role_description')}
        input={<TextArea value={roleDescription} onChange={(e) => setRoleDescription(e.target.value)} />}
      />
      <Text color="black" weight="bold">
        {tn('tags')}
      </Text>
      <Tag placeholder="Add tags.." value={tags} id="add-role-tags" disabled={false} onChange={(tag) => setTags(tag)} />
      <Text color="black" weight="bold">
        {tn('status')}
      </Text>
      <Select
        value={status}
        onChange={(option) => setStatus(option)}
        optionData={[
          { value: 'active', label: tn('active') },
          { value: 'inactive', label: tn('inactive') },
        ]}
      />
    </Stack>
  );
};

export default FirstStepForm;
