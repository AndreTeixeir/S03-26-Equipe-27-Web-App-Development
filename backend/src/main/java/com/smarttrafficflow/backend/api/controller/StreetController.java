package com.smarttrafficflow.backend.api.controller;

import com.smarttrafficflow.backend.api.dto.StreetSearchResponse;
import com.smarttrafficflow.backend.domain.streets.service.StreetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/streets")
public class StreetController {

    private static final Logger log = LoggerFactory.getLogger(StreetController.class);

    private final StreetService streetService;

    public StreetController(StreetService streetService) {
        this.streetService = streetService;
    }

    @GetMapping("/search")
    public StreetSearchResponse searchStreets(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset
    ) {
        StreetSearchResponse response = streetService.search(q, limit, offset);
        log.info("GET /api/streets/search - query='{}', limit={}, offset={}, returned {} items (total {})",
                q, response.limit(), response.offset(), response.items().size(), response.total());
        return response;
    }
}
