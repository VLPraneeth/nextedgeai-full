package com.syncari.core.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.misc.DraftableModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.customer.TagRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TagService {
	@Autowired
	ConnectorService connectorService;
	@Autowired
	TagRepo tagRepo;

	public List<Tag> assign(Map<String, Object> tags, Taggable taggable, String taggedId) {
		validate(taggable, taggedId);
		if(tags == null || tags.isEmpty()) return Collections.emptyList();
		List<Tag> tagList = new ArrayList<>();
		tags.forEach((k, v) -> {
			tagList.add(new Tag(k, v, taggable, taggedId));
		});
		log.info("Adding {} tags associated with {} having id {}", tagList.size(), taggable.name(), taggedId);
		return tagRepo.saveAll(tagList);
	}

	public void remove(String tagName, Taggable taggable, String taggedId) {
		validate(taggable, taggedId);
		if(StringUtils.isBlank(tagName)) return;
		Optional<Tag> existing = tagRepo.findByNameAndTaggableAndTaggedId(tagName, taggable, taggedId);
		existing.ifPresent( tag-> {
			log.info("Deleting tag {} associated with {} having id {}", tag.getName(), taggable.name(), taggedId);
			tagRepo.delete(tag);
		});
	}
	
	public boolean hasTag(String tagName, Taggable taggable, String taggedId) {
		validate(taggable, taggedId);
		if(StringUtils.isBlank(tagName)) return false;
		Optional<Tag> existing = tagRepo.findByNameAndTaggableAndTaggedId(tagName, taggable, taggedId);
		if (existing.isPresent())
			return true;
		else return false;
	}

	public List<Tag> addTags(List<Tag> tags){
		return tagRepo.saveAll(tags);
	}

	public void removeTags(List<Tag> tags){
		tagRepo.deleteAll(tags);
	}

	public void removeTagsFor(Taggable taggable, String taggedId) {
		validate(taggable, taggedId);
		List<Tag> existing = tagRepo.findByTaggableAndTaggedId(taggable, taggedId);
		log.info("Deleting {} tags associated with {} having id {}", existing.size(), taggable.name(), taggedId);
		tagRepo.deleteAll(existing);
	}

	public List<Tag> findTagsFor(Taggable taggable, String taggedId) {
		validate(taggable, taggedId);
		return tagRepo.findByTaggableAndTaggedId(taggable, taggedId);
	}

	public Set<String> findTagsLike(String namePrefix) {
		if(StringUtils.isBlank(namePrefix)) return Set.of();
		List<Tag> tags = tagRepo.findByNameLike(namePrefix);
		return Set.copyOf(tags).stream().map(t -> t.getName()).collect(Collectors.toSet());
	}

	public Set<String> getTagNames(Taggable taggable, String id){
		return findTagsFor(taggable, id)
				.stream()
				.map(t -> t.getName())
				.collect(Collectors.toSet());
	}

	public List<Tag> cloneTags(String sourceTaggedId, String destTaggedId, Taggable taggable){
		List<Tag> sourceTags = findTagsFor(taggable, sourceTaggedId);
		sourceTags.forEach(t -> {
			t.setId(null);
			t.setTaggedId(destTaggedId);
		});
		return addTags(sourceTags);
	}

	private void validate(Taggable taggable, String taggedId) {
		if(StringUtils.isBlank(taggedId)) throw new RuntimeException("Tagged id is required");
		if(taggable == null) throw new RuntimeException("Taggable is required");
	}

	public List<Tag> updateTagsFor(String taggedId, Taggable taggable, List<Tag> incomingTags){
		// update tags for a taggable item
		validate(taggable, taggedId);
		incomingTags.forEach(t -> {
			t.setTaggable(taggable);
			t.setTaggedId(taggedId);
		});
		List<Tag> existingTags = findTagsFor(taggable, taggedId);

		// save the newly added tags and delete the removed tags
		List<Tag> removedTags = existingTags.stream().filter(t -> !incomingTags.contains(t)).collect(Collectors.toList());
		log.info("Deleting {} tags associated with {} having id {}", removedTags.size(), taggable.name(), taggedId);
		removeTags(removedTags);

		List<Tag> newTags = incomingTags.stream().filter(t -> !existingTags.contains(t)).collect(Collectors.toList());
		log.info("Adding {} tags associated with {} having id {}", removedTags.size(), taggable.name(), taggedId);
		return addTags(newTags);
	}

	public List<Tag> updateTagIds(String fromTagId, String toTagId, Taggable taggable){
		List<Tag> existingTags = this.findTagsFor(taggable, fromTagId);
		if(!toTagId.equals(fromTagId)){
			this.updateTagsFor(toTagId, Taggable.dataset, existingTags);
			// remove tags fromTagId
			this.removeTagsFor(taggable, fromTagId);
		}
		return existingTags;
	}
}
