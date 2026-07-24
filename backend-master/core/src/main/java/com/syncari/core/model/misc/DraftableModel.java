package com.syncari.core.model.misc;

import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.UUIDAuditModel;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
public abstract class DraftableModel<T> extends UUIDAuditModel{
	
	DraftStatus draftStatus=DraftStatus.NEW;
	String parentId;
	boolean ready;
	public DraftableModel(){

	}
	public boolean isDraft(){
		return draftStatus == DraftStatus.NEW;
	}

	public boolean isSubmittedForApproval(){
		return draftStatus == DraftStatus.SUBMIT_FOR_APPROVAL;
	}
	public boolean isApproved(){
		return draftStatus == DraftStatus.APPROVED;
	}

	public boolean isArchived(){
		return draftStatus == DraftStatus.ARCHIVED;
	}

	public boolean hasPublishedParent(){
		return isDraft() && parentId != null;
	}

	public abstract T makeCopy();
	
	public abstract void copyValuesFrom(T model);

}
