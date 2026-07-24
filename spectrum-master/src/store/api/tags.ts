import { capitalize } from 'utils/Fp';

import { LIST_ID } from './constants';
import { EntityTag } from './types';
import type { EntityTags, EntityTagObject } from './types';

/**
 * TagFactoryFn
 * Helper to create a tag object for controlling rtk-q cache
 *
 * Given a tag, we get a fn that will take an id and returns { type, id }
 */
type TagFactoryFn<Tag extends EntityTags, Id extends unknown = string> = (id: Id) => EntityTagObject<Tag, Id>;
const makeTagFactory = <Tag extends EntityTags, Id extends unknown = string>(type: Tag): TagFactoryFn<Tag, Id> => (
  id: Id
) => ({ type, id } as const);

/**
 * TagHelpersMap
 *
 * Generated map of tag helpers. For each Tag that we've defined we get,
 * - TagFactoryFn helper to create tag objects
 * - Tag List object ({ type, id: "LIST" })
 *
 *
 * @example
 * // The keys of the map are modified as such,
 * const EntityTag = {
 *   BATCH: "Batch",
 *   TRANSACTION: "Transaction",
 * };
 *
 * // generated tags map
 * // {
 * //    Batch: (id) => { type: "BATCH", id }
 * //    BatchList: { type: "BATCH", id: "LIST" }
 * //    Transaction: (id) => { type: "TRANSACTION", id }
 * //    TransactionList: { type: "TRANSACTION", id: "LIST" }
 * // }
 *
 */
type TagHelpersMap = {
  [P in keyof typeof EntityTag as `${typeof EntityTag[P]}`]: TagFactoryFn<typeof EntityTag[P]>;
} &
  {
    [P in keyof typeof EntityTag as `${typeof EntityTag[P]}List`]: EntityTagObject<typeof EntityTag[P], typeof LIST_ID>;
  };

export const tags = Object.values(EntityTag).reduce((acc, tagName) => {
  const makeTag = makeTagFactory(tagName);

  return {
    ...acc,
    [capitalize(tagName)]: makeTag,
    [`${capitalize(tagName)}List`]: makeTag(LIST_ID),
  };
}, {} as TagHelpersMap);

// helper exports if you want to use without `tags.`
export const { Batch, BatchList, NodeTokens, Transaction, TransactionList } = tags;
