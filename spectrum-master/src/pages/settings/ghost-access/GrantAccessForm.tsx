//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Form, Select, Button, Input, message, Row, Col } from 'antd';
import { WrappedFormUtils } from 'antd/lib/form/Form';
import { FormEventHandler, useEffect, useMemo, useState } from 'react';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { useRequestGhostAccessMutation, useGetGhostAccessQuery, useGetSyncariDevUsersQuery } from 'store/user/api';
import { getAllRoles } from 'store/user/thunks';
import { getInstances } from 'store/instances/slice';
import { selectAllInstances } from 'selectors/instanceSelectors';
import { getSubscriptions } from 'actions/subscriptionActions';

const { Option } = Select;
const { TextArea } = Input;

interface GrantAccessFormProps {
  form: WrappedFormUtils;
}

function GrantAccessForm({ form }: GrantAccessFormProps) {
  const { getFieldDecorator, getFieldValue, resetFields, validateFields } = form;
  const dispatch = useDispatch();
  const [requestGhostAccess, { isLoading }] = useRequestGhostAccessMutation();
  const [formKey, setFormKey] = useState(0);
  const [selectedOrgId, setSelectedOrgId] = useState<string | undefined>(undefined);
  const [selectedUserId, setSelectedUserId] = useState<string | undefined>(undefined);

  const allRoles = useSelector((state) => state.user.allRoles);
  const allInstances = useSelector(selectAllInstances);
  const subscriptions = useSelector((state: any) => state.subscription.subscriptions);
  const { data: activeAccessData } = useGetGhostAccessQuery({ status: 'ACTIVE' });
  const { data: syncariDevUsers, refetch: refetchUsers } = useGetSyncariDevUsersQuery();

  // Get Syncari developers who can receive ghost access
  const users = useMemo(() => {
    return syncariDevUsers || [];
  }, [syncariDevUsers]);

  // Get instances from selected subscription, filtering out those with active access for selected user
  const instances = useMemo(() => {
    if (!selectedOrgId) {
      return [];
    }
    const selectedSub = subscriptions?.find((sub: any) => sub.id === selectedOrgId);
    const subInstances = selectedSub?.instances || [];

    // If no user selected, return all instances
    if (!selectedUserId) {
      return subInstances;
    }

    // Filter out instances where the selected user already has active access
    const userActiveInstances =
      activeAccessData
        ?.filter((access: any) => access.requesterId === selectedUserId)
        .map((access: any) => access.syncariId) || [];

    return subInstances.filter((instance: any) => !userActiveInstances.includes(instance.syncariId));
  }, [subscriptions, selectedOrgId, selectedUserId, activeAccessData]);

  useEffect(() => {
    dispatch(getAllRoles());
    dispatch(getInstances());
    dispatch(getSubscriptions());
  }, [dispatch]);

  const durations = [
    { label: '8 Hours', value: '8 hours' },
    { label: '1 Day', value: '1 day' },
    { label: '2 Days', value: '2 days' },
    { label: '30 Days', value: '30 days' },
  ];

  const categories = [
    { label: 'Professional Services', value: 'Professional Services' },
    { label: 'Synapse Approval', value: 'Synapse Approval' },
    { label: 'Troubleshooting', value: 'Troubleshooting' },
    { label: 'Status Check', value: 'Status Check' },
    { label: 'Data Related Activities', value: 'Data Related Activities' },
    { label: 'Other', value: 'Other' },
  ];

  const handleSubmit: FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    validateFields(async (err, values) => {
      if (!err) {
        try {
          await requestGhostAccess({
            userId: values.userId,
            syncariId: values.syncariId,
            roleId: values.roleId,
            duration: values.duration,
            category: values.category,
            reason: values.reason || values.category,
            accessDetails: values.accessDetails,
          }).unwrap();

          message.success('Ghost access granted successfully');

          // Refetch users to ensure fresh data
          refetchUsers();

          // Reset state first
          setSelectedUserId(undefined);
          setSelectedOrgId(undefined);

          // Then reset form fields and force re-render
          setTimeout(() => {
            resetFields();
            setFormKey((prev) => prev + 1);
          }, 0);
        } catch (error: any) {
          message.error(error?.data?.message || 'Failed to grant ghost access');
        }
      }
    });
  };

  const categoryValue = getFieldValue('category');

  const handleUserChange = (value: string) => {
    // Update selected user state
    setSelectedUserId(value);
  };

  const handleSubscriptionChange = (value: string) => {
    // Update selected subscription state
    setSelectedOrgId(value);
    // Clear instance field when subscription changes
    form.setFieldsValue({ syncariId: undefined });
  };

  return (
    <Form layout="vertical" onSubmit={handleSubmit} className="grant-access-form">
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item label="User">
            {getFieldDecorator('userId', {
              rules: [{ required: true, message: 'Please select a user' }],
            })(
              <Select
                key={`user-${formKey}`}
                showSearch
                placeholder="Select user"
                optionFilterProp="children"
                onChange={handleUserChange}
                allowClear
                filterOption={(input, option: any) =>
                  option.props.children.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {users.map((user: any) => (
                  <Option key={user.id} value={user.id}>
                    {user.firstName} {user.lastName} ({user.email})
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>

        <Col span={12}>
          <Form.Item label="Subscription">
            {getFieldDecorator('orgId', {
              rules: [{ required: true, message: 'Please select a subscription' }],
            })(
              <Select
                key={`org-${formKey}`}
                showSearch
                placeholder="Select subscription"
                optionFilterProp="children"
                onChange={handleSubscriptionChange}
                filterOption={(input, option: any) =>
                  option.props.children.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {subscriptions?.map((sub: any) => (
                  <Option key={sub.id} value={sub.id}>
                    {sub.name}
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item label="Instance">
            {getFieldDecorator('syncariId', {
              rules: [{ required: true, message: 'Please select an instance' }],
            })(
              <Select
                key={`instance-${formKey}`}
                showSearch
                placeholder="Select instance"
                disabled={!selectedOrgId}
                optionFilterProp="children"
                filterOption={(input, option: any) =>
                  option.props.children.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0
                }>
                {instances?.map((instance: any) => (
                  <Option key={instance.syncariId} value={instance.syncariId}>
                    {instance.displayName} ({instance.syncariId})
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>

        <Col span={12}>
          <Form.Item label="Role">
            {getFieldDecorator('roleId', {
              rules: [{ required: true, message: 'Please select a role' }],
            })(
              <Select key={`role-${formKey}`} placeholder="Select role">
                {allRoles?.map((role: any) => (
                  <Option key={role.id} value={role.id}>
                    {role.name}
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item label="Duration">
            {getFieldDecorator('duration', {
              initialValue: '8 hours',
              rules: [{ required: true, message: 'Please select duration' }],
            })(
              <Select key={`duration-${formKey}`}>
                {durations.map((dur) => (
                  <Option key={dur.value} value={dur.value}>
                    {dur.label}
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>

        <Col span={12}>
          <Form.Item label="Access Reason">
            {getFieldDecorator('category', {
              rules: [{ required: true, message: 'Please select a reason' }],
            })(
              <Select key={`category-${formKey}`}>
                {categories.map((cat) => (
                  <Option key={cat.value} value={cat.value}>
                    {cat.label}
                  </Option>
                ))}
              </Select>
            )}
          </Form.Item>
        </Col>
      </Row>

      {categoryValue === 'Other' && (
        <Form.Item label="Additional Details">
          {getFieldDecorator('reason', {
            rules: [{ required: true, message: 'Please provide details' }],
          })(<TextArea rows={3} placeholder="Explain the reason..." />)}
        </Form.Item>
      )}

      <Form.Item label="Access Details">
        {getFieldDecorator('accessDetails', {
          rules: [{ required: true, message: 'Please provide access details' }],
        })(
          <TextArea
            rows={4}
            placeholder="Provide details about the access being granted (e.g., specific tasks, customer information, ticket number, etc.)"
          />
        )}
      </Form.Item>

      <Form.Item>
        <Button type="primary" htmlType="submit" loading={isLoading}>
          Grant Access
        </Button>
      </Form.Item>
    </Form>
  );
}

export default Form.create<GrantAccessFormProps>({ name: 'grantAccessForm' })(GrantAccessForm);
