package com.syncari.core.model.insights;

public enum DateGroupByOption {
    SECOND( "second"){
        @Override
        public String getCompatibleDataType(){
            return "datetime";
        }

    }, MINUTE("minute"){
        @Override
        public String getCompatibleDataType(){
            return "datetime";
        }

    }, HOURLY("hour"){
        @Override
        public String getCompatibleDataType(){
            return "datetime";
        }

    }, DAILY("day"){
        @Override
        public String getCompatibleDataType(){
            return "date";
        }

    },WEEKLY("week"){
        @Override
        public String getCompatibleDataType(){
            return "date";
        }

    }, MONTHLY("month"){
        @Override
        public String getCompatibleDataType(){
            return "date";
        }

    }, QUARTERLY("quarter"){
        @Override
        public String getCompatibleDataType(){
            return "date";
        }

    },YEARLY("year"){
        @Override
        public String getCompatibleDataType(){
            return "date";
        }

    };

    String value;
    private DateGroupByOption(String value){
        this.value = value;
    }
    public abstract String getCompatibleDataType();

    public String getDisplayName(){
        return name();
    }
    public String getValue(){
        return value;
    }

    public String getDescription(){
        return name();
    }
}
