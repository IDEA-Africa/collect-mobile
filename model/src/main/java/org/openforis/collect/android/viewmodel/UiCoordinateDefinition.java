package org.openforis.collect.android.viewmodel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.openforis.collect.android.viewmodel.UiSpatialReferenceSystem.LAT_LNG_SRS;

/**
 * @author Daniel Wiell
 */
public class UiCoordinateDefinition extends UiAttributeDefinition {
    private final Map<String, UiSpatialReferenceSystem> srsById = new HashMap<String, UiSpatialReferenceSystem>();
    public final List<UiSpatialReferenceSystem> spatialReferenceSystems;
    public final boolean destinationPointSpecified;
    public final boolean onlyChangedByDevice;

    public UiCoordinateDefinition(String id, String name, String label, Integer keyOfDefinitionId,
                                  boolean calculated, boolean calculatedOnlyOneTime,
                                  List<UiSpatialReferenceSystem> spatialReferenceSystems,
                                  String description, String prompt, boolean required,
                                  boolean destinationPointSpecified, boolean onlyChangedByDevice) {
        super(id, name, label, keyOfDefinitionId, calculated, calculatedOnlyOneTime, description, prompt, required);
        this.destinationPointSpecified = destinationPointSpecified;
        this.onlyChangedByDevice = onlyChangedByDevice;
        this.spatialReferenceSystems = Collections.unmodifiableList(spatialReferenceSystems);
        for (UiSpatialReferenceSystem spatialReferenceSystem : spatialReferenceSystems)
            srsById.put(spatialReferenceSystem.id, spatialReferenceSystem);
    }

    public UiSpatialReferenceSystem getById(String id) {
        UiSpatialReferenceSystem srs = srsById.get(id);
        if (srs != null)
            return srs;
        if (id.equals(LAT_LNG_SRS.id))
            return LAT_LNG_SRS;
        throw new IllegalStateException("Spatial Reference System with id " + id + " not in definition. Valid ids are " + srsById.keySet());
    }
}
