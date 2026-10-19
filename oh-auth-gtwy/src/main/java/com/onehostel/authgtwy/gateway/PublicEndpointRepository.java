package com.onehostel.authgtwy.gateway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PublicEndpointRepository extends MongoRepository<PublicEndpoint, String> {

    List<PublicEndpoint> findByEnabledTrue();

    Optional<PublicEndpoint> findByPatternAndMethod(String pattern, String method);
}
