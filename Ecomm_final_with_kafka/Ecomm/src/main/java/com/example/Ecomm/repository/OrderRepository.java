package com.example.Ecomm.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import com.example.Ecomm.entitiy.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

	List<Order> findByCustomer_Id(Long customerId);
	 long countByCustomer_Id(Long customerId);


}
