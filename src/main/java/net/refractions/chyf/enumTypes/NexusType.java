package net.refractions.chyf.enumTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum NexusType {
	TERMINAL("Terminal"),
	FLOWPATH("Flowpath"),
	WATER("Water"),
	BANK("Bank"),
	INFERRED("Inferred"),
	UNKNOWN("Unknown");
	
	static final Logger logger = LoggerFactory.getLogger(FlowpathType.class.getCanonicalName());

	private String label;
	
	private NexusType(String label) {
		this.label = label;
	}
	
	/**
	 * Takes a string value and returns the corresponding FlowpathType object.
	 * 
	 * @param nexusType string representation of the FlowpathType
	 * @return the FlowpathType corresponding to the given string representation.
	 */
	public static NexusType convert(String nexusType) {
		for(NexusType nt : values()) {
			if(nt.label.equalsIgnoreCase(nexusType)) {
				return nt;
			}
		}
		logger.warn("Invalid NexusType value: '" + nexusType + "' defaulting to 'Unknown'.");
		return UNKNOWN;
	}
	
	/**
	 * @return the string representation of this RoadClass object
	 */
	@Override
	public String toString() {
		return label;
	}

}
