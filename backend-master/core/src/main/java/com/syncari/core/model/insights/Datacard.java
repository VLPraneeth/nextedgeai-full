package com.syncari.core.model.insights;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Transient;

import com.syncari.core.model.Tag;
import com.syncari.core.model.misc.DraftableModel;

import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

@Data
@Accessors(chain = true)
public class Datacard extends DraftableModel<Datacard> {

    String name;
    String displayName;
    String description;
    List<Visualization> contents = new ArrayList<>();
    boolean seeded;
    DatacardConfig configuration = new DatacardConfig();
    @Transient
    List<Tag> tags = new ArrayList<>();
    @Transient
    String errorMsg;

    @Override
    public String toString(){
        String withoutContents  = "name : " + name + " displayName : " + displayName + " description : " + description + " seeded : " + seeded;
        return (CollectionUtils.isNotEmpty(contents)) ? withoutContents + " contents : " + contents : withoutContents;
    }

    public Datacard makeCopy(){
        Datacard dc = new Datacard().setName(name).setSeeded(seeded).setDescription(description).setDisplayName(displayName);
        if (CollectionUtils.isNotEmpty(this.getContents())){
            List<Visualization> contentsLocal = new ArrayList<>();
            this.getContents().forEach(cont -> {
                contentsLocal.add(cont.copy());
            });
            dc.setContents(contentsLocal);
        }
        // To do remove this when we will move configuration out
        dc.setConfiguration(configuration);
        return dc;
    }

	@Override
	public void copyValuesFrom(Datacard model) {
		setName(model.getName()).setContents(model.getContents()).setDisplayName(model.getDisplayName())
				.setConfiguration(model.getConfiguration()).setSeeded(model.isSeeded())
				.setDescription(model.getDescription());
	}
}
