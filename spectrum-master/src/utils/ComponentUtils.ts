import { isNotUndefined } from 'utils/TypeUtils';

/**
 * given sets of props that should not be provided at the same time, this will validate that at most 1
 * is defined
 *
 * NOTE: it's best to run this only in dev mode so customer's don't encounter any performance hits or console logs
 *
 * @example
 *
 * // contrived component with anti-pattern of conflicting flags as props that we want to ensure are not
 * // provided simultaneously
 * const OurButtonComponent = ({
 *   small,
 *   medium,
 *   large,
 *   primary,
 *   destructive,
 *   children,
 * }) => {
 *   if(process.env.NODE_ENV !== 'production') {
 *     validateMutuallyExclusiveProps({ primary, destructive }, { small, medium, large });
 *   }
 *
 *   return (
 *     <button
 *       type="button"
 *       className={cx("button", { primary, destructive, small, medium, large })}
 *     >
 *       {children}
 *     </button>
 *   );
 * };
 *
 * // component use
 * <OurButtonComponent small medium primary />
 * // => [console.error] May not provide more than 1 prop included in: {small, medium, large}.
 *
 * <OurButtonComponent small medium primary destructive />
 * // => [console.error] May not provide more than 1 prop included in: {small, medium, large}.
 * // => [console.error] May not provide more than 1 prop included in: {primary, destructive}.
 *
 * TODO: Figure out how to handle this at the type level without using a lot of type unions
 */
export const validateMutuallyExclusiveProps = <Props extends unknown>(...propSets: Partial<Props>[]) => {
  propSets.forEach((propset) => {
    if (Object.values(propset).filter((value) => isNotUndefined(value)).length > 1) {
      console.error(`May not provide more than 1 prop included in: {${Object.keys(propset).join(', ')}}.`);
    }
  });
};
