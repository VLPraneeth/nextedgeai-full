package com.syncari.core.enrich.similarweb;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VisitMetric {
    final String yearMonth;
    final double metric;
}
