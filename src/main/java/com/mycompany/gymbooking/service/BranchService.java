package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BranchRequest;
import com.mycompany.gymbooking.dto.BranchResponse;
import java.util.List;

/**
 * The 4 CRUD operations for branches (+ "get one" and a city filter).
 *
 *   C  create   → create()
 *   R  read     → findAll(), findById()
 *   U  update   → update()
 *   D  delete   → delete()
 */
public interface BranchService {

    /** @param city optional filter; null means "all cities" */
    List<BranchResponse> findAll(String city);

    BranchResponse findById(Long id);

    BranchResponse create(BranchRequest request);

    BranchResponse update(Long id, BranchRequest request);

    void delete(Long id);
}
