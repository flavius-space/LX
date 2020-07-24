/**
 * Copyright 2018- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonObject;
import heronarts.lx.LX;
import heronarts.lx.LXComponent;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;
import heronarts.lx.output.LXDatagram;
import heronarts.lx.parameter.BooleanParameter;
import heronarts.lx.parameter.BoundedParameter;
import heronarts.lx.parameter.LXParameter;
import heronarts.lx.transform.LXMatrix;

/**
 * An LXFixture is a rich LXComponent representing a physical lighting fixture which is
 * addressed by a datagram packet. This class encapsulates the ability to configure the
 * dimensions and location of the lighting fixture as well as to specify its otuput modes
 * and protocol.
 */
public abstract class LXFixture extends LXComponent implements LXFixtureContainer, LXComponent.Renamable {

  /**
   * Output datagram protocols
   */
  public static enum Protocol {
    /**
     * No network output
     */
    NONE("None"),

    /**
     * Art-Net - <a href="https://art-net.org.uk/">https://art-net.org.uk/</a>
     */
    ARTNET("Art-Net"),

    /**
     * E1.31 Streaming ACN - <a href="https://opendmx.net/index.php/E1.31">https://opendmx.net/index.php/E1.31/</a>
     */
    SACN("E1.31 Streaming ACN"),

    /**
     * Open Pixel Control - <a href="http://openpixelcontrol.org/">http://openpixelcontrol.org/</a>
     */
    OPC("OPC"),

    /**
     * Distributed Display Protocol - <a href="http://www.3waylabs.com/ddp/">http://www.3waylabs.com/ddp/</a>
     */
    DDP("DDP"),

    /**
     * Color Kinetics KiNET - <a href="https://www.colorkinetics.com/">https://www.colorkinetics.com/</a>
     */
    KINET("KiNET");

    private final String label;

    Protocol(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return this.label;
    }
  };

  private static final double POSITION_RANGE = 1000000;

  public final BooleanParameter selected =
    new BooleanParameter("Selected", false)
    .setDescription("Whether this fixture is selected for editing");

  public final BooleanParameter identify =
     new BooleanParameter("Identify", false)
     .setDescription("Causes the fixture to flash red for identification");

  public final BoundedParameter x =
    new BoundedParameter("X", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base X position of the fixture in space");

  public final BoundedParameter y =
    new BoundedParameter("Y", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base Y position of the fixture in space");

  public final BoundedParameter z =
    new BoundedParameter("Z", 0, -POSITION_RANGE, POSITION_RANGE)
    .setDescription("Base Z position of the fixture in space");

  public final BoundedParameter yaw = (BoundedParameter)
    new BoundedParameter("Yaw", 0, -360, 360)
    .setDescription("Rotation of the fixture about the vertical axis")
    .setUnits(LXParameter.Units.DEGREES);

  public final BoundedParameter pitch = (BoundedParameter)
    new BoundedParameter("Pitch", 0, -360, 360)
    .setDescription("Rotation of the fixture about the horizontal plane")
    .setUnits(LXParameter.Units.DEGREES);

  public final BoundedParameter roll = (BoundedParameter)
    new BoundedParameter("Roll", 0, -360, 360)
    .setDescription("Rotation of the fixture about its normal vector")
    .setUnits(LXParameter.Units.DEGREES);

  public final BooleanParameter enabled =
    new BooleanParameter("Enabled", false)
    .setDescription("Whether output to this fixture is enabled");

  public final BoundedParameter brightness =
    new BoundedParameter("Brightness", 1)
    .setDescription("Brightness level of this fixture");

  public final BooleanParameter mute =
    new BooleanParameter("Mute", false)
    .setDescription("Mutes this fixture, sending all black pixels");

  public final BooleanParameter solo =
    new BooleanParameter("Solo", false)
    .setDescription("Solos this fixture, no other fixtures illuminated");

  final List<LXFixture> mutableChildren = new ArrayList<LXFixture>();

  protected final List<LXFixture> children = Collections.unmodifiableList(this.mutableChildren);

  private final List<LXDatagram> mutableDatagrams = new ArrayList<LXDatagram>();

  /**
   * Publicly accessible list of the datagrams that should be sent to this fixture
   */
  public final List<LXDatagram> datagrams = Collections.unmodifiableList(this.mutableDatagrams);

  private final LXMatrix parentTransformMatrix = new LXMatrix();

  protected final LXMatrix geometryMatrix = new LXMatrix();

  protected final List<LXPoint> mutablePoints = new ArrayList<LXPoint>();

  protected LXModel model = null;

  protected LXFixtureContainer container = null;

  /**
   * Publicly accessible immutable view of the points in this fixture. Should not
   * be directly modified. Only contains this fixture's direct points, not any of
   * its children.
   */
  public final List<LXPoint> points = Collections.unmodifiableList(this.mutablePoints);

  /**
   * A deep copy of the points array used for passing to the model layer. We need a separate copy there
   * because the model is passed to the UI layer which runs on a separate thread. That copy of the points
   * shouldn't be modified by re-indexing that occurs when we modify this.
   */
  protected final List<LXPoint> modelPoints = new ArrayList<LXPoint>();

  private final Set<LXParameter> metricsParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> geometryParameters = new HashSet<LXParameter>();

  private final Set<LXParameter> datagramParameters = new HashSet<LXParameter>();

  private int index = 0;

  protected int firstPointIndex = 0;

  protected LXFixture(LX lx) {
    this(lx, "Fixture");
  }

  protected LXFixture(LX lx, String label) {
    super(lx, label);

    // Geometry parameters
    addGeometryParameter("x", this.x);
    addGeometryParameter("y", this.y);
    addGeometryParameter("z", this.z);
    addGeometryParameter("yaw", this.yaw);
    addGeometryParameter("pitch", this.pitch);
    addGeometryParameter("roll", this.roll);

    // Output parameters
    addParameter("selected", this.selected);
    addParameter("enabled", this.enabled);
    addParameter("brightness", this.brightness);
    addParameter("identify", this.identify);
    addParameter("mute", this.mute);
    addParameter("solo", this.solo);

    this.brightness.setMappable(true);
    this.enabled.setMappable(true);
    this.identify.setMappable(true);
    this.mute.setMappable(true);
    this.solo.setMappable(true);
  }

  @Override
  protected LXComponent addParameter(String path, LXParameter parameter) {
    // Disable use of MIDI/modulation mapping to modify the structure in real-time
    parameter.setMappable(false);
    return super.addParameter(path, parameter);
  }

  @Override
  public String getPath() {
    return getModelKeys()[0] + "/" + (this.index + 1);
  }

  void setIndex(int index) {
    this.index = index;
  }

  public int getIndex() {
    return this.index;
  }

  private void setContainer(LXFixtureContainer container) {
    Objects.requireNonNull(container, "Cannot set null on LXFixture.setContainer");
    if (this.container != null) {
      throw new IllegalStateException("LXFixture already has container set: " + this + " " + this.container);
    }
    this.container = container;
  }

  protected void setStructure(LXStructure structure) {
    setParent(structure);
    setContainer(structure);
    regenerate();
  }

  private void _reindexChildren() {
    int i = 0;
    for (LXFixture child : this.children) {
      child.setIndex(i++);
    }
  }

  protected void addChild(LXFixture child) {
    addChild(child, false);
  }

  void addChild(LXFixture child, boolean generateFirst) {
    Objects.requireNonNull(child, "Cannot add null child to LXFixture");
    if (this.children.contains(child)) {
      throw new IllegalStateException("Cannot add duplicate child to LXFixture: " + child);
    }
    this.mutableChildren.add(child);
    _reindexChildren();

    child.parentTransformMatrix.set(this.geometryMatrix);

    if (generateFirst) {
      child.regenerate();
    }

    // It's acceptable to remove and re-add a child to the same container
    if (child.container != this) {
      child.setParent(this);
      child.setContainer(this);
    }

    if (!generateFirst) {
      child.regenerate();
    }
  }

  protected void removeChild(LXFixture child) {
    if (!this.children.contains(child)) {
      throw new IllegalStateException("Cannot remove non-existent child from LXFixture: " + this + " " + child);
    }
    this.mutableChildren.remove(child);
    _reindexChildren();

    // Notify the structure of change, rebuild will occur
    fixtureGenerationChanged(this);
  }

  // Invoked when a child fixture has been altered
  public final void fixtureGenerationChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureGenerationChanged(fixture);
    }
  }

  // Invoked when a child fixture has had its geometry altered
  public final void fixtureGeometryChanged(LXFixture fixture) {
    if (this.container != null) {
      this.container.fixtureGeometryChanged(fixture);
    }
  }

  /**
   * Adds a parameter which impacts the number of LEDs that are in the fixture.
   * Changes to these parameters require re-generating the whole points array.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addMetricsParameter(String path, LXParameter parameter) {
    addParameter(path, parameter);
    this.metricsParameters.add(parameter);
    return this;
  }

  /**
   * Adds a parameter which impacts the position of points in the fixture.
   * Changes to these parameters do not require rebuilding the points array, but
   * the point positions are updated and model change notifications are
   * required.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addGeometryParameter(String path, LXParameter parameter) {
    addParameter(path, parameter);
    this.geometryParameters.add(parameter);
    return this;
  }

  /**
   * Adds a parameter which impacts the output datagrams of the fixture. Whenever
   * one is changed, the datagrams will be regenerated.
   *
   * @param path Path to parameter
   * @param parameter Parameter
   * @return this
   */
  protected LXFixture addDatagramParameter(String path, LXParameter parameter) {
    addParameter(path, parameter);
    this.datagramParameters.add(parameter);
    return this;
  }

  @Override
  public void onParameterChanged(LXParameter p) {
    super.onParameterChanged(p);
    if ((this.container != null) && !this.isLoading) {
      if (this.metricsParameters.contains(p)) {
        // Note: this will rebuild this fixture and trigger the structure
        // to rebuild as well
        regenerate();
      } else if (this.geometryParameters.contains(p)) {
        // We don't need to rebuild the whole model here, just update the
        // geometry for affected fixtures
        regenerateGeometry();
      } else if (this.datagramParameters.contains(p)) {
        regenerateDatagrams();
      }
    }
    if (p == this.solo) {
      if (this.solo.isOn()) {
        this.lx.structure.soloFixture(this);
      }
    }
  }

  /**
   * An internal utility class which dynamically keeps the index values inside
   * an index buffer up to date and in sync with this fixture. Custom fixture
   * classes should use this construct via {@link #toDynamicIndexBuffer()}
   * in order to keep their datagram indices in sync with the
   */
  private class DynamicIndexBuffer {

    private final int start;
    private final int num;
    private final int stride;
    private final int[] indexBuffer;

    private DynamicIndexBuffer(int start, int num, int stride) {
      this.start = start;
      this.num = num;
      this.stride = stride;
      this.indexBuffer = new int[this.num];
      update();
    }

    private void update() {
      for (int i = 0; i < this.num; ++i) {
        this.indexBuffer[i] = getPoint(this.start + i * this.stride).index;
      }
    }
  }

  /**
   * Get an index buffer version of this fixture. The index buffer will be dynamic
   * and have its point indices updated automatically anytime this fixture is moved
   * or the larger structure is rearranged. The buffer will stop being updated
   * if this fixture's metrics are changed or if it is regenerated for any other
   * reason.
   *
   * @return Index buffer of the points in this fixture
   */
  protected int[] toDynamicIndexBuffer() {
    return toDynamicIndexBuffer(0, size());
  }

  /**
   * Get an index buffer version of this fixture. The index buffer will be dynamic
   * and have its point indices updated automatically anytime this fixture is moved
   * or the larger structure is rearranged. The buffer will stop being updated
   * if this fixture's metrics are changed or if it is regenerated for any other
   * reason.
   *
   * @param start Start index relative to this fixture
   * @param num Total number of points
   * @return Index buffer of the points in this fixture
   */
  protected int[] toDynamicIndexBuffer(int start, int num) {
    return toDynamicIndexBuffer(0, num, 1);
  }

  /**
   * Get an index buffer version of this fixture. The index buffer will be dynamic
   * and have its point indices updated automatically anytime this fixture is moved
   * or the larger structure is rearranged. The buffer will stop being updated
   * if this fixture's metrics are changed or if it is regenerated for any other
   * reason.
   *
   * @param start Start index relative to this fixture
   * @param num Total number of points
   * @param stride How many points to stride over for each step
   * @return Index buffer of the points in this fixture
   */
  protected int[] toDynamicIndexBuffer(int start, int num, int stride) {
    DynamicIndexBuffer dynamicIndexBuffer = new DynamicIndexBuffer(start, num, stride);
    this.dynamicIndexBuffers.add(dynamicIndexBuffer);
    return dynamicIndexBuffer.indexBuffer;
  }

  private final List<DynamicIndexBuffer> dynamicIndexBuffers = new ArrayList<DynamicIndexBuffer>();

  protected void regenerateDatagrams() {
    // Dispose of all these datagrams
    for (LXDatagram datagram : this.datagrams) {
      datagram.dispose();
    }
    this.mutableDatagrams.clear();

    // Dynamic index buffers are no good anymore
    this.dynamicIndexBuffers.clear();

    // Rebuild
    this.isInBuildDatagrams = true;
    buildDatagrams();
    this.isInBuildDatagrams = false;
  }

  private boolean isInBuildDatagrams = false;

  /**
   * Subclasses must override this method to provide an implementation that
   * produces the necessary set of datagrams for this fixture to be sent.
   * The subclass should call {@link #addDatagram(LXDatagram)} for each datagram.
   */
  protected abstract void buildDatagrams();

  /**
   * Subclasses may override this method to update their datagrams in the
   * case that the point indexing of this fixture has changed. Datagrams
   * may be removed and readded inside this method if necessary. If the
   * {@link DynamicIndexBuffer} class has been used to construct indices for
   * datagrams, then no action should typically be necessary.
   */
  protected void reindexDatagrams() {}

  /**
   * Subclasses call this method to add a datagram to thix fixture. This may only
   * be called from within the buildDatagrams() function.
   *
   * @param datagram Datagram to add
   */
  protected void addDatagram(LXDatagram datagram) {
    if (!this.isInBuildDatagrams) {
      throw new IllegalStateException("May not add datagrams from outside buildDatagrams() method");
    }
    Objects.requireNonNull(datagram, "Cannot add null datagram to LXFixture.addDatagram");
    if (this.mutableDatagrams.contains(datagram)) {
      throw new IllegalStateException("May not add duplicate LXDatagram to LXFixture: " + datagram);
    }
    this.mutableDatagrams.add(datagram);
  }

  /**
   * Subclasses call this method to remove a datagram from the fixture. This may only
   * be performed from within the reindexDatagrams or buildDatagrams methods.
   *
   * @param datagram Datagram to remove
   */
  protected void removeDatagram(LXDatagram datagram) {
    if (!this.isInBuildDatagrams) {
      throw new IllegalStateException("May not remove datagrams from outside reindexDatagrams() method");
    }
    if (!this.mutableDatagrams.contains(datagram)) {
      throw new IllegalStateException("May not remove non-existent LXDatagram from LXFixture: " + datagram + " " + this);
    }
    this.mutableDatagrams.remove(datagram);
  }

  /**
   * Can be used by subclasses to extend regenerate
   */
  public void onRegenerate() {}

  /**
   * Invoked when this fixture has been loaded or added to some container. Will
   * rebuild the points and the metrics, and notify container of the change to
   * this fixture's generation
   */
  protected void regenerate() {
    // We may have a totally new size, blow out the points array and rebuild
    int numPoints = size();
    this.mutablePoints.clear();
    for (int i = 0; i < numPoints; ++i) {
      LXPoint p = new LXPoint();
      p.index = this.firstPointIndex + i;
      this.mutablePoints.add(p);
    }

    // A new model will have to be created, forget these points
    this.model = null;
    this.modelPoints.clear();

    // Regenerate our geometry, note that we bypass regenerateGeometry()
    // here because we don't need to notify our container about the change. We're
    // going to notify them after this of even more substantive generation change.
    _regenerateGeometry();

    // Rebuild datagram objects
    regenerateDatagrams();

    onRegenerate();

    // Let our container know that our structural generation has changed
    if (this.container != null) {
      this.container.fixtureGenerationChanged(this);
    }
  }

  private void regenerateGeometry() {
    _regenerateGeometry();
    if (this.container != null) {
      this.container.fixtureGeometryChanged(this);
    }
  }

  /**
   * Subclasses may override this if they perform geometric transformations in a
   * different order or using totally different parameters. The supplied parameter is a
   * mutable matrix which will initially hold the value of the parent transformation matrix.
   * It can then be further manipulated based upon the parameters.
   *
   * @param geometryMatrix The geometry transformation matrix for this object
   */
  protected void computeGeometryMatrix(LXMatrix geometryMatrix) {
    float degreesToRadians = (float) Math.PI / 180;
    geometryMatrix.translate(this.x.getValuef(), this.y.getValuef(), this.z.getValuef());
    geometryMatrix.rotateY(this.yaw.getValuef() * degreesToRadians);
    geometryMatrix.rotateX(this.pitch.getValuef() * degreesToRadians);
    geometryMatrix.rotateZ(this.roll.getValuef() * degreesToRadians);
  }

  protected void _regenerateGeometry() {
    // Reset and compute the transformation matrix based upon geometry parameters
    this.geometryMatrix.set(this.parentTransformMatrix);
    computeGeometryMatrix(this.geometryMatrix);

    // Regenerate the point geometry
    regeneratePointGeometry();

    // No indices have changed but points may have moved, we are not going
    // to rebuilt the entire model, but we do need to update the locations
    // of these points in their reflected deep copies, if those have already
    // been made
    if (this.model != null) {
      this.model.transform.set(this.geometryMatrix);
    }
    if (!this.modelPoints.isEmpty()) {
      int i = 0;
      for (LXPoint p : this.points) {
        this.modelPoints.get(i++).set(p);
      }
    }
  }

  private final LXMatrix _computePointGeometryMatrix = new LXMatrix();

  private void regeneratePointGeometry() {
    this._computePointGeometryMatrix.set(this.geometryMatrix);
    computePointGeometry(this._computePointGeometryMatrix, this.points);

    // Regenerate children
    for (LXFixture child : this.children) {
      child.parentTransformMatrix.set(this.geometryMatrix);
      child._regenerateGeometry();
    }
  }

  /**
   * This method should be implemented by subclasses to generate the geometry of the
   * fixture any time its geometry parameters have changed. The correct number of points
   * will have already been computed, and merely need to have their positions set.
   *
   * @param transform A transform matrix representing the fixture's position
   * @param points The list of points that need to have their positions set
   */
  protected abstract void computePointGeometry(LXMatrix transform, List<LXPoint> points);

  /**
   * Reindex the points in this fixture. Package-level access, should only ever
   * be called by LXStructure. Subclasses should not use.
   *
   * @param startIndex Buffer index for the start of this fixture
   */
  final void reindex(int startIndex) {
    _reindex(startIndex);
  }

  // Internal private recursive implementation
  protected boolean _reindex(int startIndex) {
    boolean somethingChanged = false;
    if (this.firstPointIndex != startIndex) {
      somethingChanged = true;
      this.firstPointIndex = startIndex;
      for (LXPoint p : this.points) {
        p.index = startIndex++;
      }
    }

    // Reindex our children
    startIndex = this.firstPointIndex + this.points.size();
    for (LXFixture child : this.children) {
      if (child._reindex(startIndex)) {
        somethingChanged = true;
      }
      startIndex += child.totalSize();
    }

    // Only update index buffers and datagrams if any indices were changed
    if (somethingChanged) {
      for (DynamicIndexBuffer dynamicIndexBuffer : this.dynamicIndexBuffers) {
        dynamicIndexBuffer.update();
      }
      reindexDatagrams();
    }

    return somethingChanged;
  }

  protected LXModel instantiateModel(List<LXPoint> points, LXModel[] children, String[] keys) {
    return new LXModel(points, children, keys);
  }

  /**
   * Constructs an LXModel object for this Fixture
   *
   * @return Model representation of this fixture
   */
  public LXModel toModel() {
    // Creating a new model, clear our set of points
    this.modelPoints.clear();

    // Note: we make a deep copy here because a change to the number of points in one
    // fixture will alter point indices in all fixtures after it. When we're in multi-threaded
    // mode, that point might have been passed to the UI, which holds a reference to the model.
    // The indices passed to the UI cannot be changed mid-flight, so we make new copies of all
    // points here to stay safe.
    for (LXPoint p : this.points) {
      this.modelPoints.add(new LXPoint(p));
    }

    // Now iterate over our children and add their points too
    List<LXModel> childModels = new ArrayList<LXModel>();
    for (LXFixture child : this.children) {
      LXModel childModel = child.toModel();
      for (LXPoint p : childModel.points) {
        this.modelPoints.add(p);
      }
      childModels.add(childModel);
    }

    // Generate any submodels references into of this fixture
    for (Submodel submodel : toSubmodels()) {
      childModels.add(submodel);
    }

    // Okay, good to go, construct the model
    LXModel model = instantiateModel( this.modelPoints, childModels.toArray(new LXModel[0]), getModelKeys() );
    model.transform.set(this.geometryMatrix);
    return this.model = model;
  }

  private List<LXPoint> subpoints(int start, int n, int stride) {
    List<LXPoint> subpoints = new ArrayList<LXPoint>(n);
    for (int i = 0; i < n; ++i) {
      subpoints.add(this.modelPoints.get(start + i*stride));
    }
    return subpoints;
  }

  /**
   * Helper class to ensure that Submodels are *only* constructed
   * using the points from the produced LXModel array. No other
   * constructors are allowed.
   */
  public class Submodel extends LXModel {

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     */
    public Submodel(int start, int n) {
      this(start, n, 1);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param keys Model type key identifier for submodel
     */
    public Submodel(int start, int n, String ... keys) {
      this(start, n, 1, keys);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     */
    public Submodel(int start, int n, int stride) {
      this(start, n, stride, LXModel.Key.MODEL);
    }

    /**
     * Subclasses may use this helper to construct a submodel object from a set of
     * points in this model.
     *
     * @param start Start index
     * @param n Number of points in the submodel
     * @param stride Stride size for selecting submodel points
     * @param keys Model type key identifier for submodel
     */
    public Submodel(int start, int n, int stride, String ... keys) {
      super(subpoints(start, n, stride), keys);
      this.transform.set(geometryMatrix);
    }
  }

  /**
   * Subclasses should implement, specifying the type key of this fixture in the model
   * hierarchy.
   *
   * @return String key for the model type
   */
  protected String getModelKey() {
    return "model";
  }

  /**
   * Subclasses may override to return an array of multiple key types.
   *
   * @return List of model key types for this fixture
   */
  protected String[] getModelKeys() {
    return new String[] { getModelKey() };
  }

  protected final static Submodel[] NO_SUBMODELS = new Submodel[0];

  /**
   * Subclasses may override when they specify submodels
   *
   * @return Array of submodel objects
   */
  protected Submodel[] toSubmodels() {
    return NO_SUBMODELS;
  }

  /**
   * Subclasses must implement to specify the number of points in the fixture.
   * This does not include the number of points that are in children.
   *
   * @return number of points immediately in the fixture
   */
  protected abstract int size();

  /**
   * Returns the offset of this fixture in the index buffer
   *
   * @return Offset into the index buffer
   */
  public final int getIndexBufferOffset() {
    return this.firstPointIndex;
  }

  /**
   * Returns a copy of the geometry matrix for this fixture
   *
   * @return Copy of geometry matrix
   */
  public LXMatrix getGeometryMatrix() {
    return new LXMatrix(this.geometryMatrix);
  }

  /**
   * Returns the geometry transformation matrix, copied into the given matrix
   *
   * @param m LXMatrix object to copy into
   * @return Geometric transformation matrix, copied into parameter value
   */
  public LXMatrix getGeometryMatrix(LXMatrix m) {
    return m.set(this.geometryMatrix);
  }

  /**
   * Total points in this model and all its submodels
   *
   * @return Total number of points in this model and all submodels
   */
  public final int totalSize() {
    int sum = size();
    for (LXFixture child : this.children) {
      sum += child.totalSize();
    }
    return sum;
  }

  /**
   * Retrieves the point at a given offset in this fixture. This may recursively descend into
   * child fixtures.
   *
   * @param i Index relative to this fixture
   * @return Point at that index, if any
   */
  private LXPoint getPoint(int i) {
    // Check directly owned points first
    if (i < this.points.size()) {
      return this.points.get(i);
    }
    // Not in those, go to subfixtures...
    int ci = i - this.points.size();
    for (LXFixture fixture : children) {
      int fixtureTotalSize = fixture.totalSize();
      if (ci < fixtureTotalSize) {
        return fixture.getPoint(ci);
      }
      ci -= fixtureTotalSize;
    }
    throw new IllegalArgumentException("Point index " + i + " exceeds fixture bounds: " + this + " (" + totalSize() + ")");
  }

  @Override
  public void dispose() {
    for (LXFixture child : this.children) {
      child.dispose();
    }
    for (LXDatagram datagram : this.datagrams) {
      datagram.dispose();
    }
    this.mutableDatagrams.clear();
    super.dispose();
  }

  // Flag to avoid unnecessary work while parameters are being loaded... we'll fix
  // everything *after* the parameters are all loaded.
  private boolean isLoading = false;

  @Override
  public void load(LX lx, JsonObject obj) {
    this.isLoading = true;
    super.load(lx, obj);
    this.isLoading = false;

    // Regenerate the whole thing once
    if (this.container != null) {
      regenerate();
    }
  }
}
