package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.Branch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for branches. Spring Data writes the SQL from the method names:
 *
 *   findAllByOrderByNameAsc()          → SELECT * FROM branches ORDER BY name ASC
 *   findAllByCityIgnoreCaseOrderByNameAsc("amman")
 *                                      → SELECT * FROM branches WHERE LOWER(city) = 'amman' ORDER BY name
 *   existsByNameIgnoreCase("Abdoun")   → is there already a branch with this name?
 *   existsByNameIgnoreCaseAndIdNot("Abdoun", 3)
 *                                      → same, but ignore branch 3 (used when updating branch 3 itself)
 *   findByNameIgnoreCase("Abdoun Branch") → the branch with that name, if any (used by DataSeeder)
 */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByOrderByNameAsc();

    List<Branch> findAllByCityIgnoreCaseOrderByNameAsc(String city);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Branch> findByNameIgnoreCase(String name);
}
