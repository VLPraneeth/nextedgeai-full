import { useSelector } from 'react-redux';

import { useEnhancedSelector } from 'hooks/redux';

import { RootState } from '../../reducers';
import { ServiceCredentialsState } from './slice';

export const selectCredential = (state: RootState) => state.credential;
export const useSelectCredential = () => useEnhancedSelector<ServiceCredentialsState>(selectCredential);

export const selectCredentialModalData = (state: RootState) => state.credential;
export const useCredentialModalData = () => useSelector(selectCredentialModalData);
