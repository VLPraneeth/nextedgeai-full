import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

export interface GraphEvent {
  action: ValuesOf<typeof AppConstants.GRAPH_ACTION>;
  item: {
    id: string;
    type: ValuesOf<typeof AppConstants.GRAPH_ITEM_TYPE>;
    source: Source;
    target: Source;
  };
}

export interface Item {
  id: string;
  source: Source;
  target: Source;
}

export interface Source {
  id: string;
}
