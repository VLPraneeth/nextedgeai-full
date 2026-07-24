package com.syncari.api.rest.controllers.data;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class Renderer {
	private String title;
    private RenderType renderType = RenderType.form;
    List<WizardStep> steps;
}
