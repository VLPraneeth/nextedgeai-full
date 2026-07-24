package com.syncari.core.model.misc.fragment;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class FragmentSharePreference {
    private Set<String> hidden = new HashSet<>();
}
