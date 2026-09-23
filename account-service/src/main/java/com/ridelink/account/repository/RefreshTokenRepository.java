package com.ridelink.account.repository;

import com.ridelink.account.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUserId(String userId);
}