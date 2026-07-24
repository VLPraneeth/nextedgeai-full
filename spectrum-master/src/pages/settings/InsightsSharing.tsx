import { RouteComponentProps } from '@reach/router';
import { Button, Modal, Radio, message } from 'antd';
import { useEffect, useState } from 'react';

import EmailInput from 'components/EmailInput';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Text } from 'components/typography';
import {
  useCreateAllowedDomainsMutation,
  useFetchDomainsInUseMutation,
  useGetAllowedDomainsQuery,
} from 'store/insights-studio';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { DOMAIN_REGEX } from 'utils/RegexUtil';

import './InsightsSharing.scss';

const tn = tNamespaced('Settings.InsightsSharing');

export default function InsightsSharing({ location, uri }: RouteComponentProps) {
  const allowSharingWithOptions = [
    {
      label: tn('all_email_domains'),
      value: 'all_email_domains',
    },
    {
      label: tn('custom_list'),
      value: 'custom_list',
    },
  ] as const;
  const [domains, setDomains] = useState<string[]>([]);
  const [confirmationModalVisible, setConfirmationModalVisible] = useState(false);
  const { data: allowedDomains } = useGetAllowedDomainsQuery();
  const [fetchDomainsInUse, { data: domainsInUse }] = useFetchDomainsInUseMutation();
  const [domainError, setDomainError] = useState<string>();

  const allowSharingWithFromAPI: typeof allowSharingWithOptions[number]['value'] = allowedDomains?.domains.length
    ? 'custom_list'
    : 'all_email_domains';

  const [allowSharingWith, setAllowSharingWith] = useState<typeof allowSharingWithOptions[number]['value']>(
    allowSharingWithFromAPI
  );
  const [createAllowedDomains, { isLoading }] = useCreateAllowedDomainsMutation();

  function handleModalClose() {
    setConfirmationModalVisible(false);
  }

  function saveDomains() {
    createAllowedDomains({
      domains: allowSharingWith === 'custom_list' ? domains : [],
    })
      .unwrap()
      .then((data) => {
        message.success(tn('domains_saved'));
        setConfirmationModalVisible(false);
        setDomains(data.domains);
      })
      .catch((error) => message.error(getRtkQueryErrorMessage(error)));
  }

  useEffect(() => {
    if (allowedDomains?.domains.length) {
      setDomains(allowedDomains.domains);
      setAllowSharingWith('custom_list');
    } else {
      setAllowSharingWith('all_email_domains');
    }
  }, [allowedDomains]);

  function onDomainsChange(values: string[]) {
    const _domains = values.map((domain) => domain.trim());
    const domain = _domains[_domains.length - 1];
    const areDomainsValid = _domains.every((domain) => DOMAIN_REGEX.test(domain));

    if (areDomainsValid) {
      setDomains(_domains);
      setDomainError(undefined);
    } else {
      setDomainError(tc('domain_invalid', { domain }));
    }
  }

  return (
    <div className="insights-sharing">
      <h2>{tn('allowed_email_domains')}</h2>
      {domainError && (
        <InlineMessage type="error" title={domainError}>
          {domainError}
        </InlineMessage>
      )}
      <InputWithLabel
        label={tn('allow_sharing_with')}
        input={
          <Radio.Group
            value={allowSharingWith}
            onChange={(e) => {
              setDomainError(undefined);
              setAllowSharingWith(e.target.value);
            }}>
            {allowSharingWithOptions.map((option) => (
              <div>
                <Radio className="insights-sharing__radio" key={option.value} value={option.value}>
                  {option.label}
                </Radio>
              </div>
            ))}
          </Radio.Group>
        }
      />

      {allowSharingWith === 'custom_list' && (
        <div className="insights-sharing__emails">
          <EmailInput value={domains} onChange={onDomainsChange} placeholder={tn('type_email_domains')} />
        </div>
      )}

      <Button
        type="primary"
        className="insights-sharing__save-button"
        onClick={() => {
          setDomainError(undefined);
          fetchDomainsInUse({ domains: allowSharingWith === 'custom_list' ? domains : [] })
            .unwrap()
            .then((domainsInUse) => {
              if (domainsInUse.length) {
                setConfirmationModalVisible(true);
              } else {
                saveDomains();
              }
            })
            .catch((error) => message.error(getRtkQueryErrorMessage(error)));
        }}
        loading={isLoading}>
        {tn('save_changes')}
      </Button>

      <Modal
        title={tn('save_domain_confirmation_title')}
        visible={confirmationModalVisible}
        onCancel={handleModalClose}
        onOk={saveDomains}
        centered
        footer={
          <>
            <Button onClick={handleModalClose}>{tc('cancel')}</Button>
            <Button type="primary" onClick={saveDomains} loading={isLoading}>
              {tc('save')}
            </Button>
          </>
        }>
        <Text className="dashboard-expired__text" beDangerous>
          {tn('save_domain_confirmation_body', {
            domains: domainsInUse?.join(', '),
          })}
        </Text>
      </Modal>
    </div>
  );
}
