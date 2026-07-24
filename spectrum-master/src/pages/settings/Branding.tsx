import { useState, useMemo, FormEventHandler, useEffect } from 'react';
import { RouteComponentProps } from '@reach/router';
import { Alert, Button, Col, Form, Icon, Input, message, Row, Upload } from 'antd';
import { RcFile } from 'antd/lib/upload/interface';

import { HStack, Stack } from 'components/layout';
import { FieldLabelTooltip } from 'components/inputs/FieldGroup';
import Can from 'components/Can';
// import { ColorPickerGrid } from 'components/color-picker-grid/ColorPickerGrid';

import { useWindowTitle } from 'hooks/windowTitle';
import { put } from 'utils/AjaxUtil';
import { tNamespaced } from 'utils/i18nUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { useGetBrandingQuery, useResetBrandingMutation } from 'store/branding/api';

import './Branding.less';

const t = tNamespaced('Settings.Branding');

const MAX_LOGO_FILE_SIZE = 3145728;

const Branding = ({}: RouteComponentProps) => {
  const [logo, setLogo] = useState<RcFile | string>('');
  const [logoSquare, setLogoSquare] = useState<RcFile | string>('');
  const [brandName, setBrandName] = useState<string>('Brand name');
  const [brandColor, setBrandColor] = useState<string>('#578CEB');

  const { data: branding, refetch } = useGetBrandingQuery();
  const [resetBranding, { isLoading: isResetLoading }] = useResetBrandingMutation();
  const [isSaveLoading, setSaveLoading] = useState(false);

  useWindowTitle(t('page_title'));

  // If there is a photo uploaded, display it in the image field
  const logoSrc = useMemo(() => (typeof logo !== 'string' ? URL.createObjectURL(logo) : DataUrlConstants.ORG_LOGO), [
    logo,
  ]);

  const logoSquareSrc = useMemo(
    () => (typeof logoSquare !== 'string' ? URL.createObjectURL(logoSquare) : DataUrlConstants.ORG_LOGOSQUARE),
    [logoSquare]
  );

  useEffect(() => {
    if (branding) {
      setBrandName(branding?.name);
      setBrandColor(branding?.color);
    }
  }, [branding]);

  const handleSubmit: FormEventHandler<HTMLFormElement> = (e) => {
    e.preventDefault();
    save();
  };

  const reload = () => window.location.assign(RouteConstants.SETTINGS_BRANDING);

  const save = async () => {
    setSaveLoading(true);
    const formBody = new FormData();
    formBody.set('logo', logo);
    formBody.set('logoSquare', logoSquare);
    formBody.set('name', brandName);
    formBody.set('color', brandColor);

    put(DataUrlConstants.BRAND, formBody)
      .then((resp) => {
        setSaveLoading(false);
        message.success('Branding saved successfully');
        refetch();
        reload();
      })
      .catch((error) => {
        setSaveLoading(false);
        message.error(error?.response?.data?.message);
      });
  };

  const resetForm = async () => {
    const result: Record<string, any> = await resetBranding();
    if (result?.error) {
      message.error(`${t('reset_error')}: ${result?.error?.data?.error} | ${result?.error?.data?.message}`, 7);
    } else {
      setBrandName(result?.data?.name);
      setBrandColor(result?.data?.color);
      message.success('Branding reset successfully');
      reload();
    }
  };

  const validateFormData = () =>
    !brandName ||
    !brandColor ||
    (typeof logo !== 'string' && logo?.size > MAX_LOGO_FILE_SIZE) ||
    (typeof logoSquare !== 'string' && logoSquare?.size > MAX_LOGO_FILE_SIZE);

  return (
    <Form onSubmit={handleSubmit} autoComplete="off" className="synri-settings-brnading-container">
      <Stack spacing="lg">
        <Row>
          <Alert banner type="info" message="The changes made here may take a few minutes to update." />
        </Row>
        <Row align="middle" type="flex">
          <Col>
            <label className="synri-label" htmlFor="company-logo">
              {t('fields.company_logo_wide')}
            </label>
            <FieldLabelTooltip icon="info-circle">{t('fields.company_logo_wide_tooltip')}</FieldLabelTooltip>
            <Row align="middle" type="flex" gutter={16}>
              <Col>
                <img className="org-logo" src={logoSrc} alt="Organization brnading logo" />
              </Col>
              <Col>
                <Upload
                  accept=".png,.jpg,.jpeg"
                  beforeUpload={(file) => {
                    if (file?.size > MAX_LOGO_FILE_SIZE) {
                      message.error('The file exceeds permitted size.');
                    } else {
                      setLogo(file);
                    }
                    return false;
                  }}
                  fileList={
                    typeof logo !== 'string'
                      ? [
                          {
                            uid: logo?.uid,
                            size: logo?.size,
                            type: logo?.type,
                            name: logo?.name,
                            status: logo?.size > MAX_LOGO_FILE_SIZE ? 'error' : 'done',
                            response: 'File size exceeds limit of 3MB',
                          },
                        ]
                      : []
                  }
                  multiple={false}
                  onRemove={() => {
                    setLogo('');
                  }}>
                  <Can permission={AllPermissions.BRANDING_EDIT}>
                    <Button>
                      <Icon type="upload" />
                      {t('fields.upload_image')}
                    </Button>
                  </Can>
                </Upload>
              </Col>
            </Row>
          </Col>
        </Row>
        <Row align="middle" type="flex">
          <Col>
            <label className="synri-label" htmlFor="company-logo">
              {t('fields.company_logo_square')}
            </label>
            <FieldLabelTooltip icon="info-circle">{t('fields.company_logo_square_tooltip')}</FieldLabelTooltip>
            <Row align="middle" type="flex" gutter={16}>
              <Col>
                <img className="org-logo-square" src={logoSquareSrc} alt="Organization brnading logo" />
              </Col>
              <Col>
                <Upload
                  accept=".png,.jpg,.jpeg"
                  beforeUpload={(file) => {
                    if (file?.size > MAX_LOGO_FILE_SIZE) {
                      message.error('The file exceeds permitted size.');
                    } else {
                      setLogoSquare(file);
                    }
                    return false;
                  }}
                  fileList={
                    typeof logoSquare !== 'string'
                      ? [
                          {
                            uid: logoSquare?.uid,
                            size: logoSquare?.size,
                            type: logoSquare?.type,
                            name: logoSquare?.name,
                            status: logoSquare?.size > MAX_LOGO_FILE_SIZE ? 'error' : 'done',
                            response: 'File size exceeds limit of 3MB',
                          },
                        ]
                      : []
                  }
                  multiple={false}
                  onRemove={() => {
                    setLogoSquare('');
                  }}>
                  <Can permission={AllPermissions.BRANDING_EDIT}>
                    <Button>
                      <Icon type="upload" />
                      {t('fields.upload_image')}
                    </Button>
                  </Can>
                </Upload>
              </Col>
            </Row>
          </Col>
        </Row>
        <Row align="middle" type="flex">
          <Form.Item required validateStatus={!brandName ? 'error' : ''}>
            <label className="synri-label" htmlFor="brandName">
              {t('fields.brand_name')}
            </label>
            <FieldLabelTooltip icon="info-circle">{t('fields.brand_name_tooltip')}</FieldLabelTooltip>
            <div>
              <Input
                id="brand-name"
                className="sync-brand-name"
                required
                name="brandName"
                value={brandName}
                onChange={(e) => {
                  setBrandName(e.target.value);
                }}
                placeholder={t('fields.brand_name_placeholder')}
                maxLength={20}
              />
            </div>
          </Form.Item>
        </Row>
        <Row align="middle" type="flex">
          <Form.Item
            required
            validateStatus={!brandColor ? 'error' : ''}
            // validateStatus={validateStatus}
          >
            {/* <ColorPickerGrid
              onChange={(newColor) => {
                setBrandColor(newColor);
              }}
              color={brandColor}
              label={t('fields.brand_color')}
            /> */}
            <label className="synri-label" htmlFor="brandColor">
              {t('fields.brand_color')}
            </label>
            <Input
              id="brand-color"
              name="brandColor"
              className="sync-brand-color"
              placeholder="#578CEB"
              prefix={
                <div
                  style={{
                    width: '16px',
                    height: '16px',
                    backgroundColor: brandColor,
                    border: '1px solid #d9d9d9',
                  }}></div>
              }
              value={brandColor}
              onChange={(e) => {
                const value = e.target.value;
                setBrandColor(value);
              }}
            />
          </Form.Item>
        </Row>
        <HStack spacing="lg">
          <Can permission={AllPermissions.BRANDING_EDIT}>
            <Button onClick={() => resetForm()} type="default" loading={isResetLoading}>
              {t('buttons.reset')}
            </Button>
          </Can>
          <Can permission={AllPermissions.BRANDING_EDIT}>
            <Button htmlType="submit" key="ok" type="primary" loading={isSaveLoading} disabled={validateFormData()}>
              {t('buttons.save')}
            </Button>
          </Can>
        </HStack>
      </Stack>
    </Form>
  );
};

export default Branding;
