package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BranchRequest;
import com.mycompany.gymbooking.dto.BranchResponse;
import java.util.List;

public interface BranchService {

    /** @param city optional filter; null returns branches in all cities */
    List<BranchResponse> findAll(String city);

    BranchResponse findById(Long id);

    BranchResponse create(BranchRequest request);

    BranchResponse update(Long id, BranchRequest request);

    void delete(Long id);
}
