package com.example.rdbrepository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription,Long> {

    List<PushSubscription> findByMemberIdAndActiveTrue(Long memberId);

}
