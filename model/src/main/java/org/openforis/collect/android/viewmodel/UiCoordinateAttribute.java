package org.openforis.collect.android.viewmodel;

import java.util.Locale;

/**
 * @author Daniel Wiell
 */
public class UiCoordinateAttribute extends UiAttribute {
    private Double x;
    private Double y;
    private UiSpatialReferenceSystem spatialReferenceSystem;

    public UiCoordinateAttribute(int id, boolean relevant, UiCoordinateDefinition definition) {
        super(id, relevant, definition);
    }

    public synchronized Double getX() {
        return x;
    }

    public synchronized void setX(Double x) {
        this.x = x;
    }

    public synchronized Double getY() {
        return y;
    }

    public synchronized void setY(Double y) {
        this.y = y;
    }

    public synchronized UiSpatialReferenceSystem getSpatialReferenceSystem() {
        return spatialReferenceSystem;
    }

    public synchronized void setSpatialReferenceSystem(UiSpatialReferenceSystem spatialReferenceSystem) {
        this.spatialReferenceSystem = spatialReferenceSystem;
    }

    public UiCoordinateDefinition getDefinition() {
        return (UiCoordinateDefinition) super.getDefinition();
    }

    @Override
    public String valueAsString() {
        return isEmpty() ? null : String.format(Locale.ENGLISH, "x: %f y: %f SRS: %s", x, y, spatialReferenceSystem.id);
    }

    public boolean isEmpty() {
        return x == null || y == null;
    }

    public String format() {
        return (x == null ? "Unspecified" : x) + ", " + (y == null ? "Unspecified" : y);
    }

    public String toString() {
        return getLabel() + ": " + format();
    }
}
