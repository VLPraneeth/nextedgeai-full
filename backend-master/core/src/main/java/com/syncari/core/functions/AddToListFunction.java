package com.syncari.core.functions;

import com.syncari.core.model.*;
import com.syncari.core.quickstart.v2.dependency.DependencyService;
import com.syncari.core.validation.GraphValidationUtil;
import com.syncari.core.validation.ValidationContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Component(FunctionConstants.ADD_TO_LIST)
public class AddToListFunction extends ListMutateFunctions implements DependencyService {
}
