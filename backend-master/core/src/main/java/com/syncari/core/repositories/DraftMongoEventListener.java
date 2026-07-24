package com.syncari.core.repositories;

import java.util.Optional;

import com.syncari.core.model.MappingGraph;
import org.apache.commons.lang.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

import com.syncari.core.model.misc.DraftableModel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DraftMongoEventListener extends AbstractMongoEventListener<DraftableModel> {
	@Autowired
	DraftRepoFactory repoFacory;

	@Override
	public void onBeforeConvert(BeforeConvertEvent<DraftableModel> event) {
		if (!isNewEntity(event) && !isDraft(event)) {
			if(!event.getSource().getClass().isAssignableFrom(MappingGraph.class)){
				return;
			}
            var g = (MappingGraph) event.getSource();
            if(BooleanUtils.isTrue(g.getForceSave())) {
              return;
            }
			DraftableRepo repo = repoFacory.getRepo(event.getSource().getClass());
			Optional draftFor = repo.findActiveDraftFor(event.getSource().getId());
			if (draftFor.isPresent()) {
				throw new RuntimeException(String.format("Cannot update %s with id %s as it has a draft",
						event.getSource().getClass().getSimpleName(), event.getSource().getId()));
			}
		}

		super.onBeforeConvert(event);
	}

	private boolean isNewEntity(BeforeConvertEvent<DraftableModel> event) {
		return event.getSource().getId() == null;
	}

	private boolean isDraft(BeforeConvertEvent<DraftableModel> event) {
		return event.getSource().getParentId() != null;
	}

}
