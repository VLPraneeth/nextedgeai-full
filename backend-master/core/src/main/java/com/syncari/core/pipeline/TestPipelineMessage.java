package com.syncari.core.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TestPipelineMessage {
	String syncariId;
	String pipelineTestId;
	
	public TestPipelineMessage() {}
}
