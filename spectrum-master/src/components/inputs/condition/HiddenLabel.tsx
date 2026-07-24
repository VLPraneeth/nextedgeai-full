const HiddenLabel = ({ children, ...props }: JSX.IntrinsicElements['label']) => (
  <label className="synri-hidden-label" {...props}>
    {children}
  </label>
);

export default HiddenLabel;
