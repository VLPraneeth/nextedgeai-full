//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { SerializedError } from '@reduxjs/toolkit';
import { FetchBaseQueryError } from '@reduxjs/toolkit/dist/query';
import { Modal } from 'antd';
import ObjectID from 'bson-objectid';
import { Dispatch, ReactNode, SetStateAction, useCallback, useEffect } from 'react';
import { useContext, useState, createContext, useMemo } from 'react';

import { Text } from 'components/typography';
import {
  useDeleteCategoryMutation,
  useGetCategoriesListQuery,
  useSaveCategoriesMutation,
} from 'store/data-quality-v2/api';
import { DfiV2Category, DfiV2CategoryUpdate } from 'store/data-quality-v2/types';
import { tc } from 'utils/i18nUtil';

import { useDataQuality } from '../DataQuality.hooks';

export interface CategoriesContextProps {
  categories?: DfiV2Category[];
  setCategories: Dispatch<SetStateAction<DfiV2Category[]>>;
  addCategory?: () => void;
  isLoading: boolean;
  resetCategories: () => void;
  updateCategory: ({ name, id }: { name: string; id: string }) => void;
  deleteCategory: (id: string) => void;
  getCategory: (id: string) => DfiV2Category | undefined;
  saveCategories: () => Promise<any>;
  categoriesError: FetchBaseQueryError | SerializedError | undefined;
  hasChanges: boolean;
}

export interface CategoriesContextProviderProps {
  children: ReactNode;
}

const CategoriesContext = createContext<CategoriesContextProps>({
  categories: [],
  setCategories: () => {},
  addCategory: () => {},
  isLoading: false,
  resetCategories: () => {},
  updateCategory: () => {},
  deleteCategory: () => {},
  getCategory: () => undefined,
  saveCategories: () => Promise.resolve(),
  categoriesError: undefined,
  hasChanges: false,
});

export const useCategoriesContext = () => useContext(CategoriesContext);

export const CategoriesContextProvider = ({ children }: CategoriesContextProviderProps) => {
  const [categories, setCategories] = useState<DfiV2Category[]>([]);

  const { categoriesMatch, graphVersion } = useDataQuality();

  const { data, isFetching, isLoading, refetch, error: categoriesError } = useGetCategoriesListQuery();

  const [batchUpdateCategories] = useSaveCategoriesMutation();
  const [deleteCat] = useDeleteCategoryMutation();

  useEffect(() => {
    if (data && !isFetching && !isLoading) {
      setCategories((current) => {
        const res = data.map((cat) => {
          return {
            ...cat,
            updated: false,
            isNew: false,
          };
        });
        return res;
      });
    }
  }, [data, isFetching, isLoading]);

  useEffect(() => {
    if (!categoriesMatch?.entityId) {
      setCategories(() => []);
    } else {
      refetch();
    }
  }, [categoriesMatch?.entityId, refetch]);

  const addCategory = useCallback(() => {
    setCategories([
      ...(categories || []),
      {
        id: ObjectID.generate(),
        type: 'custom',
        isNew: true,
        updated: false,
      },
    ]);
  }, [categories]);

  const updateCategory = useCallback(
    ({ id, name }: { id: string; name: string }) => {
      setCategories(
        categories.map((cat) => {
          if (cat.id === id) {
            return {
              ...cat,
              name,
              updated: true,
            };
          }
          return cat;
        })
      );
    },
    [categories]
  );

  const deleteCategory = useCallback(
    (categoryId: string) => {
      if (graphVersion && categoriesMatch?.entityId) {
        const categoryName = categories.find((cat) => cat.id === categoryId)?.name || '';
        Modal.confirm({
          title: 'Delete Category',
          content: <Text beDangerous>{`Are you sure you want to delete <b>${categoryName}</b>`}</Text>,
          okText: tc('delete'),
          cancelText: tc('cancel'),
          onOk: () => {
            deleteCat({
              categoryId,
            });
          },
        });
      }
    },
    [graphVersion, categoriesMatch?.entityId, categories, deleteCat]
  );
  const resetCategories = useCallback(() => {
    setCategories([]);
  }, []);

  const getCategory = useCallback(
    (id: string) => {
      return categories?.find((cat) => cat.id === id);
    },
    [categories]
  );

  const saveCategories = useCallback(async () => {
    const savePayload: DfiV2CategoryUpdate[] = categories
      .filter((cat) => {
        return cat.type === 'custom' && Boolean(cat.name);
      })
      .map((cat) => {
        return {
          name: cat.name || '',
          type: 'custom',
          id: cat.isNew ? null : cat.id,
        };
      });
    return batchUpdateCategories({
      categories: savePayload,
    }).unwrap();
  }, [batchUpdateCategories, categories]);

  const hasChanges = useMemo(() => {
    return Boolean(categories?.find((cat) => cat.isNew || cat.updated));
  }, [categories]);

  const contextValue = useMemo(() => {
    return {
      addCategory,
      categories,
      setCategories,
      isLoading: isFetching || isLoading,
      hasChanges,
      resetCategories,
      updateCategory,
      deleteCategory,
      getCategory,
      saveCategories,
      categoriesError,
    };
  }, [
    addCategory,
    categories,
    categoriesError,
    deleteCategory,
    getCategory,
    hasChanges,
    isFetching,
    isLoading,
    resetCategories,
    saveCategories,
    updateCategory,
  ]);

  return <CategoriesContext.Provider value={contextValue}>{children}</CategoriesContext.Provider>;
};
