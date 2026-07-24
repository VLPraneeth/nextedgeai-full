//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { StoryFn, Meta } from '@storybook/react';

import AgTable, { AgTableProps } from './AgTable';

const BaseDesignParameters = {
  design: {
    type: 'figma',
    url: 'https://syncari.atlassian.net/wiki/spaces/UX/pages/1081278595/Tables',
  },
};

const defaultColDef = { flex: 1 };
const columns = [
  {
    headerName: 'Name',
    field: 'name',
  },
  {
    headerName: 'Type',
    field: 'type',
  },
];

const Template: StoryFn<AgTableProps> = (args) => (
  <AgTable
    defaultColDef={defaultColDef}
    domLayout="autoHeight"
    suppressCellSelection
    columnDefs={columns}
    rowData={[
      {
        name: 'Name',
        type: 'Type',
      },
    ]}
  />
);

export const BaseTable = Template.bind({});
BaseTable.args = {};
BaseTable.storyName = 'Table';
BaseTable.parameters = BaseDesignParameters;

export default {
  title: 'Table/Table',
  component: AgTable,
} as Meta;
