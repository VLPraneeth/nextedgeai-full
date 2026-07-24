//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, Col, Form, Icon, Input, Row, Select, Upload } from 'antd';
import { WrappedFormUtils } from 'antd/lib/form/Form';
import { RcFile } from 'antd/lib/upload/interface';
import { FormEventHandler, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch } from 'hooks/redux';
import { useUserData } from 'store/user/selector.hooks';
import { updatePassword, updateProfile } from 'store/user/thunks';
import AppConstants from 'utils/AppConstants';
import { setWindowTitle } from 'utils/AppUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { timeZoneNames } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';

import './EditProfile.less';

const t = tNamespaced('Profile');
const errorMessages = {
  confirmPassword: t('errors.confirm_password_required'),
  firstName: t('errors.first_name_required'),
  lastName: t('errors.last_name_required'),
  newPassword: t('errors.new_password_required'),
  passwordMatch: t('errors.new_confirm_should_match'),
  timeZone: t('errors.time_zone_required'),
};

function EditProfile({ form }: { form: WrappedFormUtils }) {
  const { getFieldDecorator, getFieldValue, getFieldsValue, isFieldsTouched, resetFields, validateFields } = form;

  const dispatch = useEnhancedDispatch();
  const user = useUserData();

  // Handling timeZone and photo as controlled fields for better
  // control over page state and behavior
  const [timeZone, setTimeZone] = useState(user.timeZone);
  const [photo, setPhoto] = useState<RcFile | string>('');
  useEffect(() => setTimeZone(user.timeZone), [user.timeZone]);

  const profileFieldsTouched = isFieldsTouched(['firstName', 'lastName']) || photo !== '' || timeZone !== user.timeZone;
  const passwordFieldsTouched = isFieldsTouched(['currentPassword', 'newPassword']);
  const formIsTouched = profileFieldsTouched || passwordFieldsTouched;

  // If there is a photo uploaded, display it in the image field
  const photoURL = useMemo(
    () => (typeof photo !== 'string' ? URL.createObjectURL(photo) : DataUrlConstants.PROFILE_PHOTO),
    [photo]
  );

  const handleSubmit: FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    validateFields((err) => {
      if (!err) {
        save();
      }
    });
  };

  const save = async () => {
    const { currentPassword, firstName, lastName, newPassword } = getFieldsValue();

    if (profileFieldsTouched) {
      const profileData = {
        id: user.id,
        firstName,
        lastName,
        photo,
        timeZone,
      };
      await dispatch(updateProfile(profileData));
    }

    if (passwordFieldsTouched) {
      await dispatch(updatePassword({ currentPassword, newPassword, id: user.id }));
    }

    resetForm();
  };

  const validateNewPassword = (rule: any, value: string, callback: any) => {
    if (getFieldValue('currentPassword') && !value) {
      callback(errorMessages.newPassword);
    } else {
      callback();
    }
  };

  const validateConfirmPassword = (rule: any, value: string, callback: any) => {
    const { newPassword } = getFieldsValue();
    const newPassWithoutConfirmPass = newPassword && newPassword !== value;

    if (newPassWithoutConfirmPass) {
      callback(errorMessages.passwordMatch);
    } else {
      callback();
    }
  };

  const timeZoneOptions = useMemo(() => {
    return timeZoneNames.map((tz, index) => (
      <Select.Option key={index} value={tz}>
        {tz}
      </Select.Option>
    ));
  }, []);

  useEffect(() => {
    setWindowTitle(t('title'));
  }, []);

  const resetForm = () => {
    resetFields();
    setPhoto('');
  };

  if (!user.id) {
    return <div />;
  }

  return (
    <Form onSubmit={handleSubmit} autoComplete="off" className="synri-settings-profile-container">
      <Row>
        <Col md={10} xl={8}>
          <Form.Item>
            <label className="synri-label" htmlFor="editProfile_firstName">
              {t('fields.firstName')}
            </label>
            {getFieldDecorator('firstName', {
              initialValue: user.firstName,
              rules: [
                {
                  required: true,
                  whitespace: true,
                  message: errorMessages.firstName,
                },
              ],
            })(<Input autoComplete="given-name" />)}
          </Form.Item>
          <Form.Item>
            <label className="synri-label" htmlFor="editProfile_lastName">
              {t('fields.lastName')}
            </label>
            {getFieldDecorator('lastName', {
              initialValue: user.lastName,
              rules: [
                {
                  required: true,
                  whitespace: true,
                  message: errorMessages.lastName,
                },
              ],
            })(<Input autoComplete="family-name" />)}
          </Form.Item>

          <label className="synri-label" htmlFor="editProfile_email">
            {t('fields.email')}
          </label>
          <Input defaultValue={user.email} disabled id="email" name="email" />
          <div className="password-section">
            <div className="synri-label change-password">{t('fields.change_password')}</div>

            <Form.Item>
              {getFieldDecorator('currentPassword', {
                rules: [
                  {
                    required: false,
                    whitespace: true,
                    message: errorMessages.lastName,
                  },
                ],
              })(
                <Input.Password
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  placeholder={t('fields.current_password')}
                />
              )}
            </Form.Item>
            <Form.Item>
              {getFieldDecorator('newPassword', {
                rules: [{ validator: validateNewPassword }],
              })(
                <Input.Password
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  placeholder={t('fields.new_password')}
                />
              )}
            </Form.Item>
            <Form.Item>
              {getFieldDecorator('confirmPassword', {
                rules: [{ validator: validateConfirmPassword }],
              })(
                <Input.Password
                  autoComplete={AppConstants.INPUT_AUTOCOMPLETE_OPTIONS.OFF}
                  placeholder={t('fields.confirm_password')}
                />
              )}
            </Form.Item>
          </div>
        </Col>
        <Col md={{ span: 13, offset: 1 }} xl={8}>
          <span className="synri-label">{t('fields.image')}</span>

          <Row align="middle" type="flex">
            <Col>
              <div
                className="profile-photo"
                style={{
                  backgroundImage: `url(${photoURL})`,
                }}
              />
            </Col>
            <Col>
              <Upload
                accept=".png,.jpg,.jpeg"
                beforeUpload={(file) => {
                  setPhoto(file);
                  return false;
                }}
                fileList={typeof photo !== 'string' ? [photo] : []}
                multiple={false}
                onRemove={() => {
                  setPhoto('');
                }}>
                <Button>
                  <Icon type="picture" />
                  {t('fields.upload_image')}
                </Button>
              </Upload>
            </Col>
          </Row>

          <div className="synri-label">{t('fields.time_zone')}</div>
          <Select key={user.timeZone} id="time-zone" onChange={setTimeZone} showSearch value={timeZone}>
            {timeZoneOptions}
          </Select>
        </Col>
      </Row>
      <Row>
        <Col>
          <Button disabled={!formIsTouched} htmlType="submit" type="primary">
            {t('save_changes')}
          </Button>
          {formIsTouched && (
            <Button onClick={() => resetForm()} type="link">
              Reset
            </Button>
          )}
        </Col>
      </Row>
    </Form>
  );
}

export default Form.create({ name: 'editProfile' })(EditProfile);
