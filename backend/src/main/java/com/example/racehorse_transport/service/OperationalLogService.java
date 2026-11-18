package com.example.racehorse_transport.service;



public interface OperationalLogService {

    void record(String action, String target, Integer key, String note, String actor);
}
