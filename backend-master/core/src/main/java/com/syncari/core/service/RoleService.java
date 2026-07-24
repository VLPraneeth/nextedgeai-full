package com.syncari.core.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.syncari.core.model.Role;
import com.syncari.core.repositories.customer.RoleRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RoleService {
    @Autowired
    private RoleRepo repo;

    public List<Role> getAllRoles() {
        return repo.findAll();
    }
}
