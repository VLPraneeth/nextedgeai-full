export const useRealtimePipeline = () => {
  return {
    isEnabled: true,
    setRealtimePipeline: (configuration: any) => {
      const { enable, webhookId, endpoint } = configuration;
      return undefined;
    },
  };
};
