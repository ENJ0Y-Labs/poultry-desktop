package com.grantinofarms.poultry.domain;

import java.time.LocalDate;

public record EggLedgerEvent(LocalDate date, int goodCollected, int goodSold) {}
