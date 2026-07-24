package com.syncari.core.model.misc;

import java.time.temporal.ChronoUnit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Duration {
	private Float duration;
    private ChronoUnit durationUnit = ChronoUnit.SECONDS;
}
