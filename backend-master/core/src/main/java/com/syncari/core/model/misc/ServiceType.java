package com.syncari.core.model.misc;

public enum ServiceType {
    Clearbit,
    Slack {
      public boolean isConnector() {
          return true;
      }
    },
    Zoominfo {
        @Override
        public boolean isConnector() {
            return true;
        }
    },
    Insideview {
        @Override
        public boolean isConnector() {
            return true;
        }
    },
    Similarweb {
        public boolean isConnector() {
            return true;
        }
    },
    Apexanalytix {
        public boolean isConnector() {
            return true;
        }
    },
    genericApiKey {
        public boolean isConnector() {
            return true;
        }
    },
    genericBearerToken {
        public boolean isConnector() {
            return true;
        }
    },
    genericSimpleOAuth {
        public boolean isConnector() {
            return true;
        }
    },
    Aidentified {
        public boolean isConnector() {
            return true;
        }
    },
    Salesintel{
        public boolean isConnector() {
            return true;
        }
    },
    Msteams {
        public boolean isConnector() {
            return true;
        }
    };
    public boolean isConnector() {
        return false;
    }
}
