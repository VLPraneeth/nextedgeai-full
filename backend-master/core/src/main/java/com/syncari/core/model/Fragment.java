package com.syncari.core.model;

import com.syncari.core.model.misc.fragment.FragmentGraph;
import com.syncari.core.model.util.Scope;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;


@Data
@Accessors(chain = true)
public class Fragment extends UUIDAuditModel {

    @NotNull
    String name;
    String description;
    @NotNull
    String ownerUserId;
    @NotNull
    Scope scope;
    boolean shared;
    String sharedItemId;
    @NotNull
    FragmentGraph fragmentGraph;

    @Transient
    List<Tag> tags = new ArrayList<>();

    public Fragment makeCopy(){
        return new Fragment().setName(name).setDescription(description).setScope(scope).setOwnerUserId(ownerUserId)
                .setFragmentGraph(fragmentGraph).setTags(tags).setSharedItemId(sharedItemId).setShared(shared);
    }

    public void validate(){
        validateCondition(Scope.SCHEMA.equals(scope), i18n("fragment_schema_scope_not_supported"));
        validateCondition(isShared() && StringUtils.isBlank(sharedItemId), i18n("shared_fragment_missing_shared_item"));
        validateCondition(fragmentGraph==null, i18n("fragment_null_graph"));
        fragmentGraph.validate();

    }
}
