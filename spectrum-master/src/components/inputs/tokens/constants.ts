export const TOKEN_BEGIN_SENTINEL = '{{';
export const TOKEN_END_SENTINEL = '}}';

export const SyncariToken = 'SYNCARI-TOKEN';
const baseTokenRegex = String.raw`${TOKEN_BEGIN_SENTINEL}(.+?)${TOKEN_END_SENTINEL}`;
export const TokenRegex = new RegExp(baseTokenRegex, 'g');
export const SingleTokenRegex = new RegExp(`^${baseTokenRegex}$`, 'g');
