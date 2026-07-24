import { message } from 'antd';
import { LabeledValue, SelectValue } from 'antd/lib/select';
import { Dispatch, SetStateAction, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { Step, Steps } from 'components/steps';
import { useEnhancedDispatch } from 'hooks/redux';
import { useCreateRoleMutation } from 'store/access-control/api';
import { setChangesInProgressModal } from 'store/app/actions';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced } from 'utils/i18nUtil';

import FirstStepForm from './FirstStepForm';
import LastStepForm from './LastStepForm';
import StepTwoForm from './StepTwoForm';

import './AddRoleWizard.scss';

export enum StepsIndex {
  firstStep = 0,
  secondStep = 1,
  lastStep = 2,
}

export enum RoleFormTypes {
  add = 'add',
  edit = 'edit',
}

interface AddRoleWizardProps {
  close: () => void;
  currentStepIndex: number;
  setCurrentStepIndex: Dispatch<SetStateAction<number>>;
  visible: boolean;
}

export enum RoleStatus {
  active = 'active',
  inactive = 'inactive',
}

export default function AddRoleWizard({ close, currentStepIndex, setCurrentStepIndex, visible }: AddRoleWizardProps) {
  const tn = tNamespaced('Settings.AccessControl.AddRole');
  const [roleName, setRoleName] = useState('');
  const [roleDescription, setRoleDescription] = useState('');
  const [tags, setTags] = useState<string[] | []>([]);
  const [status, setStatus] = useState<SelectValue>(RoleStatus.active);

  const [rolePermissions, setRolePermissions] = useState<LabeledValue[]>([]);
  const [roleUsers, setRoleUsers] = useState<LabeledValue[]>([]);

  const dispatch = useEnhancedDispatch();

  const [createRoleMutation] = useCreateRoleMutation();

  const resetFields = () => {
    setRoleName('');
    setRoleDescription('');
    setTags([]);
    setStatus(RoleStatus.active);
    setRolePermissions([]);
    setRoleUsers([]);
  };

  const handleCancel = () => {
    if (roleName.length > 3) {
      dispatch(
        setChangesInProgressModal({
          visible: true,
          discardChangesAction: () => {
            close();
            resetFields();
            setCurrentStepIndex(0);
          },
          keepEditingAction: () => null,
        })
      );
    } else {
      setCurrentStepIndex(0);
      resetFields();
      close();
    }
  };

  const createRole = () => {
    createRoleMutation({
      name: roleName.trim(),
      description: roleDescription,
      tags,
      active: status === RoleStatus.active,
      privileges: rolePermissions.map((item) => item.key),
      users: roleUsers.map((item) => item.key),
    })
      .then((res) => {
        if ('error' in res) {
          message.error(getRtkQueryErrorMessage(res?.error));
          resetFields();
          close();
          return;
        }
        message.success(tn('role_created', { roleName }));
        resetFields();
        close();
      })
      .catch((err) => {
        resetFields();
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
            formType={RoleFormTypes.add}
            rolePermissions={rolePermissions}
            roleUsers={roleUsers}
            setRolePermissions={setRolePermissions}
            setRoleUsers={setRoleUsers}
          />
        )}
        {currentStepIndex === StepsIndex.lastStep && (
          <LastStepForm
            roleName={roleName.trim()}
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
              (currentStepIndex === StepsIndex.firstStep && roleName.trim().length === 0) ||
              (currentStepIndex === StepsIndex.secondStep && rolePermissions.length === 0)
            }
            onClick={() => {
              if (currentStepIndex === StepsIndex.lastStep) {
                createRole();
                setCurrentStepIndex(0);
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
