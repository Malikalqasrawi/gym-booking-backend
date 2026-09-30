package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Branch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByOrderByNameAsc();

    List<Branch> findAllByCityIgnoreCaseOrderByNameAsc(String city);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Branch> findByNameIgnoreCase(String name);
}
