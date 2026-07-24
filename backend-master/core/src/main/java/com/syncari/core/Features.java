package com.syncari.core;

public enum Features {
   InsightsProvider{
        @Override
        public boolean isHidden() {
            return false;
        }
    },
    PipelineEditorV2 {
        @Override
        public boolean isHidden() {
            return false;
        }
    },
    ABAC {
      @Override
      public boolean isHidden() {
          return false;
      }
    },
    BRAND {
        @Override
        public boolean isHidden() {
            return false;
        }
    },
    NetsuiteSuiteQL {
        @Override
        public boolean isHidden() {
            return false;
        }
    },
    BusinessStudio {
        @Override
        public boolean isHidden() {
            return false;
        }
    },
    MergeRetainValue, LHSFilterChange, EntityCaching, SinksideActions, Datastore, Insights,
    UpdateReferencesOnIdMappingChange, DfiV2Provisioning;

    public boolean isHidden() {
        return true;
    }
}