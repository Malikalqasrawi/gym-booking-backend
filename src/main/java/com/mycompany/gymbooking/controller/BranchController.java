package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.BranchRequest;
import com.mycompany.gymbooking.dto.BranchResponse;
import com.mycompany.gymbooking.service.BranchService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A REST + CRUD API for branches.
 *
 * REST idea: ONE address for the resource ("/api/branches"), and the HTTP METHOD says what to do.
 *
 *   Method  URL                       CRUD     Who            Success
 *   ------  ------------------------  -------  -------------  -----------------------------
 *   GET     /api/branches             Read     logged-in      200 + list
 *   GET     /api/branches?city=Amman  Read     logged-in      200 + filtered list
 *   GET     /api/branches/{id}        Read     logged-in      200 + one branch
 *   POST    /api/branches             Create   ADMIN only     201 + new branch + Location header
 *   PUT     /api/branches/{id}        Update   ADMIN only     200 + updated branch
 *   DELETE  /api/branches/{id}        Delete   ADMIN only     204 (no body)
 *
 * "Who" is enforced in SecurityConfig, not here, so this class stays about HTTP only.
 */
@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    /** READ all. @RequestParam(required = false) → "?city=Amman" is optional. */
    @GetMapping
    public List<BranchResponse> getAll(@RequestParam(required = false) String city) {
        return branchService.findAll(city);
    }

    /** READ one. @PathVariable takes the {id} out of the URL: /api/branches/3 → id = 3 */
    @GetMapping("/{id}")
    public BranchResponse getOne(@PathVariable Long id) {
        return branchService.findById(id);
    }

    /** CREATE. Returns 201 Created and a "Location" header pointing to the new branch. */
    @PostMapping
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody BranchRequest request) {
        BranchResponse created = branchService.create(request);
        URI location = URI.create("/api/branches/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    /** UPDATE. PUT means "replace this branch's details with these". */
    @PutMapping("/{id}")
    public BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branchService.update(id, request);
    }

    /** DELETE. 204 No Content = "done, nothing to send back". */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        branchService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
