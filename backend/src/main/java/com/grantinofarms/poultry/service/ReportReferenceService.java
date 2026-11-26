package com.grantinofarms.poultry.service;

import com.grantinofarms.poultry.repository.BatchRepository;
import com.grantinofarms.poultry.repository.FarmRepository;
import com.grantinofarms.poultry.repository.FeedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportReferenceService {
    private final FarmRepository farms;
    private final BatchRepository batches;
    private final FeedRepository feeds;

    public ReportReferenceService(FarmRepository farms, BatchRepository batches, FeedRepository feeds) {
        this.farms = farms;
        this.batches = batches;
        this.feeds = feeds;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        RoleAuthorization.requireReportSubmitter();
        var farm = farms.findActive();
        if (farm == null) return Map.of("batches", List.of(), "feedTypes", List.of());
        List<Map<String, Object>> batchRows = batches.findByFarm(farm.id()).stream()
                .filter(batch -> "ACTIVE".equals(batch.status()))
                .map(batch -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", batch.id());
                    row.put("code", batch.code());
                    row.put("type", batch.type());
                    row.put("houseId", batch.houseId());
                    row.put("placementDate", batch.placementDate());
                    return row;
                }).toList();
        List<Map<String, Object>> feedRows = feeds.findTypes(farm.id()).stream()
                .filter(feed -> "ACTIVE".equals(feed.status()))
                .map(feed -> Map.<String, Object>of(
                        "id", feed.id(),
                        "name", feed.name(),
                        "unit", feed.unit(),
                        "applicableTypes", feed.applicableTypes()))
                .toList();
        return Map.of("batches", batchRows, "feedTypes", feedRows);
    }
}
