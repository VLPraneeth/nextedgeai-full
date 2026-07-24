//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import * as Reach from '@reach/router';

import FastMapperModal from 'pages/sync-studio/fast-mapper/FastMapperModal';
import { RootState } from 'reducers/index';
import { Entity, EntityFieldDraftStatus, EntityFieldStatus } from 'store/entity/types';
import * as FastMapperActions from 'store/fast-mapper/slice';
import { fireEvent, render, screen } from 'tests/helpers';
import AppConstants from 'utils/AppConstants';
import { t, tc, tNamespaced } from 'utils/i18nUtil';
import { DeepPartial } from 'utils/TypeUtils';

import { getConnector, getEntities, getMappings } from '../FastMapperModal.fixtures';
const { createHistory, createMemorySource, LocationProvider, Router } = Reach;

const { SYNCARI_NODE_STATUS } = AppConstants;

const tn = tNamespaced('FastMapperModal');
const ta = tNamespaced('AddMapping');

describe('FastMapperModal', () => {
  it('should render, add and save mapping with error', async () => {
    const setAddMappingErrorSpy = jest.spyOn(FastMapperActions, 'setAddMappingError');
    const testState: DeepPartial<RootState> = {
      entity: {
        entities: getEntities([
          {
            pipelineStatus: SYNCARI_NODE_STATUS.UNMAPPED,
          },
        ]) as Entity[],
      },
      fastMapper: {
        fastMapperVisible: true,
        fastMapperEntityId: '603ca4f25db2a7fe97e4b5b4',
        createFieldModal: {
          id: 'test-id',
          mode: 'add' as FastMapperActions.CreateFieldModalMode,
          position: {
            top: 0,
            left: 0,
          },
          visible: false,
          data: undefined,
        },
      },
      connector: getConnector(),
    };
    render(
      <LocationProvider>
        <FastMapperModal />
      </LocationProvider>,
      {
        testState,
      }
    );

    expect(await screen.findByText(tn('title', { name: 'Account' }))).toBeInTheDocument();
    expect(await screen.findByText(ta('save_mappings'))).toBeInTheDocument();
    fireEvent.click(await screen.findByText(ta('save_mappings')));
    expect(setAddMappingErrorSpy).toHaveBeenCalled();
  });

  it('should show the empty field mapping message in the mapping page', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {
        entities: getEntities() as Entity[],
      },
      fastMapper: { fastMapperVisible: true, fastMapperEntityId: '603ca4f25db2a7fe97e4b5b4' },
      connector: getConnector(),
    };
    const { getByTextWithMarkup } = render(
      <LocationProvider>
        <FastMapperModal />
      </LocationProvider>,
      {
        testState,
      }
    );

    expect(getByTextWithMarkup('No field mapping found. Click Add Mapping to start.')).toBeInTheDocument();
  });

  it('should show the browse mapping when the selected entity is not umapped', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {
        entities: getEntities() as Entity[],
      },
      fastMapper: { fastMapperVisible: true, fastMapperEntityId: '603ca4f25db2a7fe97e4b5b4' },
      connector: getConnector(),
    };
    render(
      <LocationProvider>
        <FastMapperModal />
      </LocationProvider>,
      {
        testState,
      }
    );

    expect(await screen.findByText(t('FastMapperModal.title_browse', { name: 'Account' }))).toBeInTheDocument();
    expect(await screen.findByText(tc('close'))).toBeInTheDocument();
  });

  it.skip('show the mappings in the graph', async () => {
    const testState: DeepPartial<RootState> = {
      entity: {
        entities: getEntities() as Entity[],
        connectorFieldsWithStatus: {
          '603d124e5db2a7fe97e4c350': { data: getEntities()[0].fields as any }, // Syncari
          '603ca4f25db2a7fe97e4b5b4': {
            // Synapse
            data: [
              {
                id: '603ca4f25db2a7fe97e4b5c3',
                apiName: 'Name',
                displayName: 'Account Name',
                description: null,
                dataType: 'string',
                status: EntityFieldStatus.ACTIVE,
                type: null,
                tags: [],
                values: [],
                isMapped: false,
                hasChanges: false,
                draftStatus: EntityFieldDraftStatus.APPROVED,
                readOnly: false,
                required: true,
                referenceTargetField: '',
                multiValueField: false,
                unique: false,
                watermarkField: false,
                system: false,
                idField: false,
                reference: false,
              },
              {
                id: '605cd557b561c59732d378ed',
                apiName: 'Name',
                displayName: 'Account Name',
                description: null,
                dataType: 'string',
                status: EntityFieldStatus.ACTIVE,
                type: null,
                tags: [],
                values: [],
                isMapped: false,
                hasChanges: false,
                draftStatus: EntityFieldDraftStatus.APPROVED,
                readOnly: false,
                required: true,
                referenceTargetField: '',
                multiValueField: false,
                unique: false,
                watermarkField: false,
                system: false,
                idField: false,
                reference: false,
              },
              {
                id: '605cd613b561c5977cae2f9d',
                apiName: 'Description',
                displayName: 'Account Description',
                description: null,
                dataType: 'string',
                status: EntityFieldStatus.ACTIVE,
                type: null,
                tags: [],
                values: [],
                isMapped: false,
                hasChanges: false,
                draftStatus: EntityFieldDraftStatus.APPROVED,
                readOnly: false,
                required: true,
                referenceTargetField: '',
                multiValueField: false,
                unique: false,
                watermarkField: false,
                system: false,
                idField: false,
                reference: false,
              },
            ] as any,
          },
        },
      },
      fastMapper: { fastMapperVisible: true, fastMapperEntityId: '603ca4f25db2a7fe97e4b5b4', mappings: getMappings() },
      connector: getConnector(),
    };
    render(
      <LocationProvider>
        <FastMapperModal />
      </LocationProvider>,
      {
        testState,
      }
    );

    expect(await screen.findByText(t('FastMapperModal.title_browse', { name: 'Account' }))).toBeInTheDocument();

    screen.debug(undefined, 70000);

    expect(await screen.queryAllByText('Account Name')).toHaveLength(2);
    expect(await screen.findByText(tc('close'))).toBeInTheDocument();
  });
});
