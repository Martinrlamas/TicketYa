package com.ticketya.order.service;

import com.ticketya.platform.web.PageResponse;
import com.ticketya.order.dto.response.RefundRequestResponse;
import com.ticketya.order.entity.RefundRequestStatus;
import com.ticketya.order.mapper.RefundRequestMapper;
import com.ticketya.order.repository.RefundRequestRepository;
import com.ticketya.platform.security.Identity;
import com.ticketya.platform.security.Role;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RefundQueryService {

    private final RefundRequestRepository refundRepo;
    private final RefundRequestMapper mapper;

    public RefundQueryService(RefundRequestRepository refundRepo, RefundRequestMapper mapper) {
        this.refundRepo = refundRepo;
        this.mapper = mapper;
    }

    public PageResponse<RefundRequestResponse> listarMios(Identity caller, int page, int size) {
        caller.requireAnyRole(Role.CUSTOMER);
        return PageResponse.of(refundRepo.findByCustomerId(caller.userId(), pageable(page, size)), mapper::toResponse);
    }

    /** Backoffice: ordenadas de la más antigua a la más nueva. */
    public PageResponse<RefundRequestResponse> listAll(Identity caller, RefundRequestStatus status, int page, int size) {
        caller.requireAnyRole(Role.ADMIN);
        Pageable pageable = pageable(page, size);
        var result = status == null ? refundRepo.findAll(pageable) : refundRepo.findByStatus(status, pageable);
        return PageResponse.of(result, mapper::toResponse);
    }

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "createdAt"));
    }
}
