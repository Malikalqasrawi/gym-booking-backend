package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.BlockImpactResponse;
import com.mycompany.gymbooking.dto.BlockedTimeCreatedResponse;
import com.mycompany.gymbooking.dto.BlockedTimeRequest;
import com.mycompany.gymbooking.dto.BlockedTimeResponse;
import java.util.List;

/** Branch closures and trainers' time off. Blocked times are never offered to members. */
public interface BlockedTimeService {

    /** Blocks that haven't ended yet, soonest first. */
    List<BlockedTimeResponse> upcoming();

    /** Validates the block and counts the bookings it would cancel, without saving anything. */
    BlockImpactResponse preview(BlockedTimeRequest request);

    /** Saves the block and cancels the bookings inside it, refunding paid ones in full. */
    BlockedTimeCreatedResponse create(BlockedTimeRequest request);

    /** Frees the time again. Bookings it cancelled stay cancelled. */
    void delete(Long id);
}
