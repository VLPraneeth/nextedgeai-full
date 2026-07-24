package com.syncari.karibu.rest.response;

import com.syncari.core.model.UUIDAuditModel;

public interface KaribuResponse {

    public abstract <k extends KaribuResponse, h extends UUIDAuditModel> Object populate(h object);

}
