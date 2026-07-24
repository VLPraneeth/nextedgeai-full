import { useGetAttributesTokensQuery } from './api';

// convenience hook to get the tokens for the selected node
export const useTokensForSelectedResource = ({ type, id }: any) => useGetAttributesTokensQuery({ type, id });
