import { ServiceCredentialTypeOptionsEnum } from 'store/credentials/types';

// Note: Backend metadata is inconsistent so we're doing a trasformation before building the form
export const getCredentialType = (credentialType?: string) => {
  switch (credentialType?.toLocaleLowerCase()) {
    case ServiceCredentialTypeOptionsEnum.APIKEY.toLocaleLowerCase():
      return ServiceCredentialTypeOptionsEnum.APIKEY;
    case ServiceCredentialTypeOptionsEnum.BEARERTOKEN.toLocaleLowerCase():
      return ServiceCredentialTypeOptionsEnum.BEARERTOKEN;
    case ServiceCredentialTypeOptionsEnum.OAUTH.toLocaleLowerCase():
      return ServiceCredentialTypeOptionsEnum.OAUTH;
  }
  return credentialType?.toLocaleLowerCase();
};
