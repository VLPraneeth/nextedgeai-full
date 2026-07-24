package com.syncari.connector.data;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Data
@Accessors(chain = true)
public class AuthField {
	String name;
	String dataType;
	String label;
	@Getter(lombok.AccessLevel.NONE)
	String helpSummary;

	// Adding an excluded field to hold the custom synapse helpSummary
	@ToString.Exclude
	@Getter(lombok.AccessLevel.NONE)
	String description;
	//True by default for backward compatibility
	boolean required=true;
	boolean hidden=false;
	Object defaultValue;
	List<Map<String, Object>> options;
	public AuthField() {
	}

	// Override getHelpSummary to consider description if helpSummary is blank
	public String getHelpSummary(){
		return (StringUtils.isBlank(helpSummary) && StringUtils.isNotBlank(description)) ? description : helpSummary;
	}
}
