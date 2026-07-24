package com.syncari.core.service;

import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.misc.DraftableModel;
import com.syncari.core.repositories.DraftableRepo;
import com.syncari.utils.Timer;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public abstract class DraftService<T extends DraftableModel<T>> {

    protected static final String DELETED = "DELETED";

    protected abstract DraftableRepo<T> getDraftableRepo();

    protected abstract void processArchived(T archived);

    public T approveDraft(T model) {
        return approveDraft(model, DraftStatus.APPROVED);
    }

    protected T approveDraft(T model, DraftStatus approvalStatus) {
        DraftableRepo<T> repo = getDraftableRepo();
        //Ensure model exists in DB
        Optional<T> existingDraftMayBe = repo.findById(model.getId());
        assert existingDraftMayBe.isPresent() : "Draftable Model with id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
        assert List.of(DraftStatus.APPROVAL_IN_PROGRESS,DraftStatus.APPROVED).contains(approvalStatus) : "Invalid approval status " + approvalStatus + " for approval.";
        var existingDraft = existingDraftMayBe.get();
        if (existingDraft.getParentId() == null) {
            existingDraft.setDraftStatus(approvalStatus);
            existingDraft.setReady(false);
            log.info("Approved new draft for {} with id {} ", model.getClass().getSimpleName(), model.getId());
            return repo.save(existingDraft);
        } else {
            Optional<T> existingParent = repo.findById(model.getParentId());
            assert existingParent.isPresent() : "Approved model for draft id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
            existingParent.get().copyValuesFrom(existingDraft);
            existingParent.get().setDraftStatus(approvalStatus);
            existingParent.get().setReady(false);
            existingDraft.setDraftStatus(DraftStatus.ARCHIVED);
            existingDraft.setReady(false);
            processArchived(existingDraft);
            repo.save(existingDraft);
            var saved = repo.save(existingParent.get());
            log.info("Approved {} exists with id {}. Replacing it with draft for {} ", model.getClass().getSimpleName(), model.getParentId(), model.getId());
            return saved;
        }
    }
    
    protected List<T> approveDraftBatch(List<T> models) {
      Timer timer = new Timer(100, "DraftService::approveDraftBatch", log);
      DraftableRepo<T> repo = getDraftableRepo();
      List<T> tobeSaved = new ArrayList<T>();
      List<T> tobeArchived = new ArrayList<T>();
      Map<String, T> existing = new HashMap<>();
      for (T d : repo.findAllById(models.stream().map(T::getId).collect(Collectors.toList()))) {
        existing.put(d.getId(), d);
      }
      for(T model: models) {
        Optional<T> existingDraftMayBe = Optional.ofNullable(existing.get(model.getId()));
        assert existingDraftMayBe.isPresent() : "Draftable Model with id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
        var existingDraft = existingDraftMayBe.get();
        if (existingDraft.getParentId() == null) {
          existingDraft.setDraftStatus(DraftStatus.APPROVED);
          existingDraft.setReady(false);
          log.info("Approved new draft for {} with id {} ", model.getClass().getSimpleName(), model.getId());
          tobeSaved.add(existingDraft);
        } else {
          Optional<T> existingParent = repo.findById(model.getParentId());
          assert existingParent.isPresent() : "Approved model for draft id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
          existingParent.get().copyValuesFrom(existingDraft);
          existingParent.get().setDraftStatus(DraftStatus.APPROVED);
          existingParent.get().setReady(false);
          existingDraft.setDraftStatus(DraftStatus.ARCHIVED);
          existingDraft.setReady(false);
          processArchived(existingDraft);
          tobeArchived.add(existingDraft);
          tobeSaved.add(existingParent.get());
          log.info("Approved {} exists with id {}. Replacing it with draft for {} ", model.getClass().getSimpleName(), model.getParentId(), model.getId());
        }
      }
      if(!tobeArchived.isEmpty()) {
        repo.saveAll(tobeArchived);
      }
      var res = repo.saveAll(tobeSaved);
      timer.close();
      return res;
  }

    protected T approveDummyDraft(T model, DraftStatus approvalStatus) {
        DraftableRepo<T> repo = getDraftableRepo();
        //Ensure model exists in DB
        Optional<T> existingDraftMayBe = repo.findById(model.getId());
        assert existingDraftMayBe.isPresent() : "Draftable Model with id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
        assert List.of(DraftStatus.APPROVAL_IN_PROGRESS,DraftStatus.APPROVED).contains(approvalStatus) : "Invalid approval status " + approvalStatus + " for approval.";
        var existingDraft = existingDraftMayBe.get();
        if (existingDraft.getParentId() == null) {
            existingDraft.setDraftStatus(approvalStatus);
            existingDraft.setReady(false);
            log.info("Approved new draft for {} with id {} ", model.getClass().getSimpleName(), model.getId());
            return existingDraft;
        } else {
            Optional<T> existingParent = repo.findById(model.getParentId());
            assert existingParent.isPresent() : "Approved model for draft id " + model.getId() + " not found for  " + model.getClass().getSimpleName();
            existingParent.get().copyValuesFrom(existingDraft);
            existingParent.get().setDraftStatus(approvalStatus);
            existingParent.get().setReady(false);
            existingDraft.setDraftStatus(DraftStatus.ARCHIVED);
            existingDraft.setReady(false);
            processArchived(existingDraft);
            repo.save(existingDraft);
            log.info("Approved {} exists with id {}. Replacing it with draft for {} ", model.getClass().getSimpleName(), model.getParentId(), model.getId());
            return existingParent.get();
        }
    }

    public void discardDraft(T draft) {
        DraftableRepo<T> repo = getDraftableRepo();
        Optional<T> existingDraft = repo.findById(draft.getId());
        if (existingDraft.get().getDraftStatus() != DraftStatus.NEW) {
            throw new RuntimeException("Draft cannot be discarded as it is approved");
        }
        if (!existingDraft.get().isDraft()) {
            throw new RuntimeException("Passed object is not a draft");
        }
        repo.delete(existingDraft.get());
        log.info("Discarded existing draft for {} with id {}", draft.getClass().getSimpleName(), draft.getId());
    }

    public boolean hasDraft(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        log.info("Searching for draft : " + model.getId());
        Optional<T> existingDraft = repo.findActiveDraftFor(model.getId());
        if (existingDraft.isPresent())
            log.info("Found draft : {}" , existingDraft.get().getId());
        return existingDraft.isPresent();
    }

    public Optional<T> findDraft(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        log.info("Searching for draft : {}" , model.getId());
        return repo.findActiveDraftFor(model.getId());
    }

    public void delete(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        if(model.isDraft()){
            discardDraft(model);
        } else if(model.isApproved()) {
            Optional<T> existingDraft = findDraft(model);
            existingDraft.ifPresent(draft -> {
                log.info("Updating parent of draft with id : {}. setting value as null", draft.getId());
                draft.setParentId(null);
                repo.save(draft);
            });

            log.info("Deleting published with id : {}", model.getId());
            repo.delete(model);
        }
    }


    public boolean isDraft(T model) {
        return model.getParentId() != null;
    }

    public T createDraftFor(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        T newEntity = model.makeCopy();
        newEntity.setId(null);
        newEntity.setParentId(model.getId());
        newEntity.setCreatedAt(new Date());
        newEntity.setCreatedBy(SyncariContext.getUser().getId());
        newEntity.setDraftStatus(DraftStatus.NEW);
        var saved = repo.save(newEntity);
        log.info("Created a new draft for {} with id {} ", model.getClass().getSimpleName(), saved.getId());
        return saved;
    }

    public T createDummyDraftFor(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        T newEntity = model.makeCopy();
        newEntity.setId(null);
        newEntity.setParentId(model.getId());
        newEntity.setCreatedAt(new Date());
        newEntity.setCreatedBy(SyncariContext.getUser().getId());
        newEntity.setDraftStatus(DraftStatus.NEW);
        newEntity.setId(ObjectId.get().toHexString());
        log.info("Created a new dummy draft for {} with id {} ", model.getClass().getSimpleName(), newEntity.getId());
        return newEntity;
    }

    public void discardDraftFor(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        Optional<T> existingDraft = repo.findActiveDraftFor(model.getId());
        existingDraft.ifPresent(draft-> {
            discardDraft(draft);
            log.info("Discarded existing draft for {} with id {}", model.getClass().getSimpleName(), model.getId());
        });

    }

    public T submitForApproval(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        Optional<T> existingDraftMayBe = repo.findById(model.getId());
        if (existingDraftMayBe.isPresent()) {
            var existingDraft = existingDraftMayBe.get();
            existingDraft.setDraftStatus(DraftStatus.SUBMIT_FOR_APPROVAL);
            log.info("Submitted for approval draft for {} with id {} ", model.getClass().getSimpleName(), model.getId());
            return repo.save(existingDraft);
        }
        throw new SyncariValidationException(String.format("Could not find %s with id '%s'", model.getClass().getSimpleName(), model.getId()));
    }

    public T withdrawApproval(T model) {
        DraftableRepo<T> repo = getDraftableRepo();
        Optional<T> existingDraftMayBe = repo.findById(model.getId());
        if (existingDraftMayBe.isPresent()) {
            var existingDraft = existingDraftMayBe.get();
            existingDraft.setDraftStatus(DraftStatus.NEW);
            log.info("Withdrew from approval draft for {} with id {} ", model.getClass().getSimpleName(), model.getId());
            return repo.save(existingDraft);
        }
        throw new SyncariValidationException(String.format("Could not find %s with id '%s'", model.getClass().getSimpleName(), model.getId()));
    }

}
