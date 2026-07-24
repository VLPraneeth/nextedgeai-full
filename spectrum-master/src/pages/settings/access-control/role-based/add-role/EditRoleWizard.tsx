import { message } from 'antd';
import { LabeledValue, SelectValue } from 'antd/lib/select';
import _ from 'lodash';
import { Dispatch, SetStateAction, useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { Step, Steps } from 'components/steps';
import { useEnhancedDispatch } from 'hooks/redux';
import { useEditRoleMutation, useGetRoleByIdQuery } from 'store/access-control/api';
import { UserRole } from 'store/access-control/types';
import { setChangesInProgressModal } from 'store/app/actions';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced } from 'utils/i18nUtil';

import { RoleFormTypes, RoleStatus, StepsIndex } from './AddRoleWizard';
import FirstStepForm from './FirstStepForm';
import LastStepForm from './LastStepForm';
import StepTwoForm from './StepTwoForm';

import './AddRoleWizard.scss';

interface EditRoleWizardProps {
  close: () => void;
  currentStepIndex: number;
  setCurrentStepIndex: Dispatch<SetStateAction<number>>;
  selectedRoleId: string;
  visible: boolean;
}

export default function EditRoleWizard({
  close,
  currentStepIndex,
  setCurrentStepIndex,
  selectedRoleId,
  visible,
}: EditRoleWizardProps) {
  const tn = tNamespaced('Settings.AccessControl.EditRole');
  const [roleName, setRoleName] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [tags, setTags] = useState<string[] | []>([]);
  const [status, setStatus] = useState<SelectValue>(RoleStatus.active);

  const [rolePermissions, setRolePermissions] = useState<LabeledValue[]>([]);
  const [roleUsers, setRoleUsers] = useState<LabeledValue[]>([]);

  const dispatch = useEnhancedDispatch();

  const { data, refetch } = useGetRoleByIdQuery(
    { roleId: selectedRoleId },
    { refetchOnMountOrArgChange: true, refetchOnReconnect: true, refetchOnFocus: true }
  );

  const [editRoleMutation] = useEditRoleMutation();

  const populateFields = (data: UserRole) => {
    setRoleName(data.name);
    data.description && setRoleDescription(data.description);
    data.tags && setTags(data.tags);
    setStatus(data.active ? RoleStatus.active : RoleStatus.inactive);
    setRolePermissions(
      data.privileges.map((permission) => ({ key: permission.privilegeId, label: permission.displayName }))
    );
    setRoleUsers(data.users.map((user) => ({ key: user.id, label: `${user.firstName} ${user.lastName}` })));
  };

  useEffect(() => {
    if (data) {
      populateFields(data);
    }
  }, [data, refetch, visible]);

  const handleCancel = () => {
    const dataUsers = data?.users.map((user) => user.id);
    const dataPermissions = data?.privileges.map((item) => item.privilegeId);
    const stateUsers = roleUsers.map((user) => user.key);
    const statePermissions = rolePermissions.map((user) => user.key);

    const changeHasOccurred =
      roleName !== data?.name ||
      roleDescription !== data?.description ||
      tags !== data?.tags ||
      data?.active !== (status === RoleStatus.active) ||
      !_.isEqual(dataUsers, stateUsers) ||
      !_.isEqual(dataPermissions, statePermissions);

    if (changeHasOccurred) {
      dispatch(
        setChangesInProgressModal({
          visible: true,
          discardChangesAction: () => {
            close();
            setCurrentStepIndex(0);
          },
          keepEditingAction: () => null,
        })
      );
    } else {
      close();
      setCurrentStepIndex(0);
    }
  };

  const mutateRole = () => {
    editRoleMutation({
      roleId: selectedRoleId,
      name: roleName,
      description: roleDescription,
      tags,
      active: status === 'active',
      privileges: rolePermissions.map((item) => item.key),
      users: roleUsers.map((item) => item.key),
    })
      .then((res) => {
        if ('error' in res) {
          message.error(getRtkQueryErrorMessage(res?.error));
          return;
        }
        message.success(tn('success_message', { roleName }));
        close();
        setCurrentStepIndex(0);
      })
      .catch((err) => {
        message.error(err);
      });
  };

  return (
    <DrawerPanel
      absolutePositioning
      maskClosable
      onClose={handleCancel}
      mask
      title={tn('title')}
      width="full"
      visible={visible}>
      <div className="add-role__container">
        <Steps className="add-role__steps" current={currentStepIndex} direction="vertical">
          <Step title={tn('step1_title')} />
          <Step title={tn('step2_title')} />
          <Step title={tn('step3_title')} />
        </Steps>

        {currentStepIndex === StepsIndex.firstStep && (
          <FirstStepForm
            roleName={roleName}
            roleDescription={roleDescription}
            tags={tags}
            status={status}
            setRoleName={setRoleName}
            setRoleDescription={setRoleDescription}
            setTags={setTags}
            setStatus={setStatus}
          />
        )}
        {currentStepIndex === StepsIndex.secondStep && (
          <StepTwoForm
            formType={RoleFormTypes.edit}
            rolePermissions={rolePermissions}
            roleUsers={roleUsers}
            setRolePermissions={setRolePermissions}
            setRoleUsers={setRoleUsers}
          />
        )}
        {currentStepIndex === StepsIndex.lastStep && (
          <LastStepForm
            roleName={roleName}
            roleDescription={roleDescription}
            tags={tags}
            status={status}
            rolePermissions={rolePermissions}
            roleUsers={roleUsers}
          />
        )}

        <div className="add-role__steps-footer">
          <Button onClick={handleCancel} className="page-button">
            {tn('cancel')}
          </Button>
          <Button
            className="page-button"
            disabled={currentStepIndex === StepsIndex.firstStep}
            onClick={() => setCurrentStepIndex(currentStepIndex - 1)}>
            {tn('previous')}
          </Button>
          <Button
            disabled={
              (currentStepIndex === StepsIndex.firstStep && roleName.length === 0) ||
              (currentStepIndex === StepsIndex.secondStep && rolePermissions.length === 0)
            }
            onClick={() => {
              if (currentStepIndex === StepsIndex.lastStep) {
                mutateRole();
              } else {
                setCurrentStepIndex(currentStepIndex + 1);
              }
            }}
            type="primary"
            className="page-button">
            {currentStepIndex === StepsIndex.lastStep ? tn('done') : tn('next')}
          </Button>
        </div>
      </div>
    </DrawerPanel>
  );
}
