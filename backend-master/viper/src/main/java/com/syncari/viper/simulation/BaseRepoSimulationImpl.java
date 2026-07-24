package com.syncari.viper.simulation;

import com.syncari.core.model.misc.Model;
import com.syncari.core.repositories.SyncariRepo;
import org.apache.commons.collections4.IterableUtils;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BaseRepoSimulationImpl<T extends Model> implements SyncariRepo<T> {
    @Override
    public void deleteAllById(List ids) {

    }

    @Override
    public void reset() {

    }

    @Override
    public List saveAll(Iterable entities) {
        return IterableUtils.toList(entities);
    }

    @Override
    public <S extends T> S save(S s) {
        return s;
    }

    @Override
    public Optional<T> findById(String s) {
        return Optional.empty();
    }

    @Override
    public boolean existsById(String s) {
        return false;
    }

    @Override
    public List findAll() {
        return Collections.emptyList();
    }

    @Override
    public Iterable findAllById(Iterable iterable) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(String s) {

    }

    @Override
    public void delete(T t) {

    }

    @Override
    public void deleteAll(Iterable iterable) {

    }

    @Override
    public void deleteAll() {

    }

    @Override
    public List findAll(Sort sort) {
        return Collections.emptyList();
    }

    @Override
    public <S extends T> S insert(S entity) {
        return entity;
    }

    @Override
    public Page findAll(Pageable pageable) {
        return Page.empty();
    }

    @Override
    public List findAll(Example example, Sort sort) {
        return Collections.emptyList();
    }

    @Override
    public List findAll(Example example) {
        return Collections.emptyList();
    }

    @Override
    public List insert(Iterable entities) {
        return IterableUtils.toList(entities);
    }

    @Override
    public Optional findOne(Example example) {
        return Optional.empty();
    }

    @Override
    public Page findAll(Example example, Pageable pageable) {
        return Page.empty();
    }

    @Override
    public long count(Example example) {
        return 0;
    }

    @Override
    public boolean exists(Example example) {
        return false;
    }
}
