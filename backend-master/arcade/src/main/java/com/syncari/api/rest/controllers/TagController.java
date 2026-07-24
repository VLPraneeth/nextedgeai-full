package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.ASSIGN_TAG;
import static com.syncari.core.security.Permissions.READ_TAG;
import static com.syncari.core.security.Permissions.REMOVE_TAG;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.syncari.api.rest.controllers.data.TagRequest;
import com.syncari.core.service.TagService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/tag")
public class TagController {
	@Autowired
	TagService tagService;

	@Secured(READ_TAG)
	@RequestMapping(method = RequestMethod.GET, value = "/{namePrefix}")
	public Set<String> getTagsLike(@PathVariable String namePrefix) {
		return tagService.findTagsLike(new String(Base64.getDecoder().decode(namePrefix)));
	}

	@Secured(ASSIGN_TAG)
	@RequestMapping(method = RequestMethod.POST, value = "/assign")
	public void assign(@RequestBody List<TagRequest> request) {
		request.forEach(r -> {
			tagService.assign(Map.of(r.getName(), r.getValue() == null ? true : r.getValue()), r.getType(),
					r.getTaggedId());
		});
	}

	@Secured(REMOVE_TAG)
	@RequestMapping(method = RequestMethod.POST, value = "/remove")
	public void remove(@RequestBody List<TagRequest> request) {
		request.forEach(r -> {
			tagService.remove(r.getName(), r.getType(), r.getTaggedId());
		});
	}

}
