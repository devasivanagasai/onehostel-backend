package com.onehostel.access.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.onehostel.access.constants.AccessEnums.Role;
import com.onehostel.access.model.RolePrivileges;

public interface RolePrivilegesRepository extends MongoRepository<RolePrivileges, String> {

    Optional<RolePrivileges> findByRole(Role role);

    List<RolePrivileges> findByRoleIn(Collection<Role> roles);
}
