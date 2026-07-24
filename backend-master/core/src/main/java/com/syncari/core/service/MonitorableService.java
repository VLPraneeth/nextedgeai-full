package com.syncari.core.service;

public interface MonitorableService {

    long HEART_BEAT_EXPIRY_MILLIS =2 /*hours*/* 60  /*mins*/ * 60 /*seconds*/ * 1000  /*millis*/ ;//1800000;

    void buryTheDead();
}
