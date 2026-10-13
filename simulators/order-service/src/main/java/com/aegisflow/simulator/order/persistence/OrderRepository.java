package com.aegisflow.simulator.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the service supplies composite tenant/resource keys. */
interface OrderRepository extends JpaRepository<OrderEntity, OrderKey> { }
