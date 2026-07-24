import { MergeOptionValues } from 'components/merge-options/MergeOptions.types';

import { QuickStartReviewItem, RenderInfoTypes } from './QuickStartInstallReview';

export const installReviewItems: QuickStartReviewItem[] = [
  {
    label: 'Synapses used',
    count: 2,
    renderInfo: {
      type: RenderInfoTypes.STRING_LIST,
      data: ['Salesforce', 'Hubspot'],
    },
  },
  {
    label: 'Pipelines will be merged',
    count: 3,
    renderInfo: {
      type: RenderInfoTypes.MERGED_PIPELINES,
      data: [
        {
          id: '123',
          apiName: 'account',
          displayName: 'Account',
          fields: [
            {
              id: '60e49df87c987b42ccd83c1c',
              apiName: 'ParentId',
              displayName: 'Parent Account ID',
              dataType: 'reference',
              source: MergeOptionValues.BEFORE_CORE,
              destination: MergeOptionValues.COPY,
            },
            {
              id: '60e49df87c987b42ccd83c29',
              apiName: 'PhotoUrl',
              displayName: 'Photo URL',
              dataType: 'url',
              source: MergeOptionValues.REPLACE,
              destination: MergeOptionValues.AFTER_CORE,
            },
            {
              id: '60e49df87c987b42ccd83c3b',
              apiName: 'Score',
              displayName: 'Score',
              dataType: 'double',
              source: MergeOptionValues.AFTER_SOURCE,
              destination: MergeOptionValues.BEFORE_SINK,
            },
          ],
        },
      ],
    },
  },
  {
    label: 'Pipelines will be replaced',
    count: 2,
    renderInfo: {
      type: RenderInfoTypes.REPLACED_BY_PIPELINES,
      data: [
        {
          id: '123',
          apiName: 'account',
          displayName: 'Account',
          replacementFields: [
            {
              id: '60e49df87c987b42ccd83c1c',
              apiName: 'ParentId',
              displayName: 'Parent Account ID',
              dataType: 'reference',
            },
            {
              id: '60e49df87c987b42ccd83c29',
              apiName: 'PhotoUrl',
              displayName: 'Photo URL',
              dataType: 'url',
            },
            {
              id: '60e49df87c987b42ccd83c3b',
              apiName: 'Score',
              displayName: 'Score',
              dataType: 'double',
            },
            {
              id: '60e49df87c987b42ccd83c23',
              apiName: 'ShippingCity',
              displayName: 'Shipping City',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c26',
              apiName: 'ShippingCountry',
              displayName: 'Shipping Country',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c24',
              apiName: 'ShippingState',
              displayName: 'Shipping State/Province',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c22',
              apiName: 'ShippingStreet',
              displayName: 'Shipping Street',
              dataType: 'textarea',
            },
            {
              id: '60e49df87c987b42ccd83c25',
              apiName: 'ShippingPostalCode',
              displayName: 'Shipping Zip/Postal Code',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c32',
              apiName: 'SystemModstamp',
              displayName: 'System Modstamp',
              dataType: 'datetime',
            },
            {
              id: '60e49df87c987b42ccd83c39',
              apiName: 'TotalMoneyRaised',
              displayName: 'Total Money Raised',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c3a',
              apiName: 'TwitterHandle',
              displayName: 'Twitter Handle',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c28',
              apiName: 'Website',
              displayName: 'Website',
              dataType: 'url',
            },
            {
              id: '60e49df87c987b42ccd83c37',
              apiName: 'YearStarted',
              displayName: 'Year Started',
              dataType: 'string',
            },
          ].map((field) => ({ field, replacementField: field })) as any,
        },
      ],
    },
  },
  {
    label: 'Pipelines created',
    count: 2,
    renderInfo: {
      type: RenderInfoTypes.ENTITY_PIPELINES,
      data: [
        {
          id: 'entityOne',
          displayName: 'Lead',
          fields: [
            {
              id: '60e49df87c987b42ccd83c17',
              apiName: 'AboutUs',
              displayName: 'About Us',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c2c',
              apiName: 'Description',
              displayName: 'Account Description',
              dataType: 'textarea',
            },
            {
              id: '60e49df87c987b42ccd83c16',
              apiName: 'Id',
              displayName: 'Account ID',
              dataType: 'id',
            },
            {
              id: '60e49df87c987b42ccd83c1a',
              apiName: 'Name',
              displayName: 'Account Name',
              dataType: 'string',
            },
          ],
        },
        {
          id: 'entityTwo',
          displayName: 'Account',
          fields: [
            {
              id: '60e49df87c987b42ccd83c17',
              apiName: 'AboutUs',
              displayName: 'About Us',
              dataType: 'string',
            },
            {
              id: '60e49df87c987b42ccd83c2c',
              apiName: 'Description',
              displayName: 'Account Description',
              dataType: 'textarea',
            },
            {
              id: '60e49df87c987b42ccd83c16',
              apiName: 'Id',
              displayName: 'Account ID',
              dataType: 'id',
            },
            {
              id: '60e49df87c987b42ccd83c1a',
              apiName: 'Name',
              displayName: 'Account Name',
              dataType: 'string',
            },
          ],
        },
      ],
    },
  },
  {
    label: 'Reference datasets will be used',
    count: 1,
    renderInfo: {
      type: RenderInfoTypes.STRING_LIST,
      data: ['County data'],
    },
  },
  {
    label: 'Service provider will be used',
    count: 1,
    renderInfo: {
      type: RenderInfoTypes.STRING_LIST,
      data: ['Zoominfo'],
    },
  },
];
