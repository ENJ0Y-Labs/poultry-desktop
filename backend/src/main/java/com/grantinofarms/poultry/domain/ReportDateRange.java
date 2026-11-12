package com.grantinofarms.poultry.domain;

import com.grantinofarms.poultry.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

public record ReportDateRange(LocalDate from, LocalDate to) {
    public static ReportDateRange require(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "Both start and end dates are required for a reporting range.");
        }
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "The start date must be on or before the end date.");
        }
        return new ReportDateRange(from, to);
    }

    public boolean includes(LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }
}
