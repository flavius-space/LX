/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
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

package heronarts.lx.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import heronarts.lx.output.LXDatagram;

/**
 * A utility class with a simple interface, used to procedurally construct
 * model objects. Each instance of a builder should only be used to produce one
 * model. After it's been produced, methods cannot be called to modify the
 * contents anymore. This class is significantly simpler than {@link LXModel}
 * in the amount of functionality exported, with only the components needed
 * for construction. It is mutable up until the moment it has been converted
 * into a model, at which point no further modifications are allowed.
 */
public class LXModelBuilder {

  final List<LXPoint> points = new ArrayList<LXPoint>();

  final List<LXModelBuilder> children = new ArrayList<LXModelBuilder>();

  List<String> keys = new ArrayList<String>();

  LXModel model = null;

  final List<LXDatagram> datagrams = new ArrayList<LXDatagram>();

  /**
   * Construct a model-builder with the default model key
   */
  public LXModelBuilder() {
    this(LXModel.Key.MODEL);
  }

  /**
   * Construct a model-builder with the given set of model keys
   *
   * @param keys Model type keys
   */
  public LXModelBuilder(String ... keys) {
    setKeys(keys);
  }

  private void checkEditState() {
    if (this.model != null) {
      throw new IllegalStateException("May not edit state of LXModelBuilder after toModel() has been called");
    }
  }

  /**
   * Add a key to the list of this model's key types
   *
   * @param key Model key type
   * @return this
   */
  public LXModelBuilder addKey(String key) {
    checkEditState();
    this.keys.add(key);
    return this;
  }

  /**
   * Set the model type keys array
   *
   * @param keys Key types
   * @return this
   */
  public LXModelBuilder setKeys(String ... keys) {
    checkEditState();
    this.keys.clear();
    for (String key : keys) {
      this.keys.add(key);
    }
    return this;
  }

  /**
   * Add a point to the model
   *
   * @param p Point
   * @return this
   */
  public LXModelBuilder addPoint(LXPoint p) {
    checkEditState();
    p.index = this.points.size();
    this.points.add(p);
    return this;
  }

  /**
   * Add a list of points to the model
   *
   * @param points Points
   * @return this
   */
  public LXModelBuilder addPoints(LXPoint ... points) {
    checkEditState();
    for (LXPoint p : points) {
      p.index = this.points.size();
      this.points.add(p);
    }
    return this;
  }

  /**
   * Add a child builder to this model.
   *
   * @param child Child
   * @return this
   */
  public LXModelBuilder addChild(LXModelBuilder child) {
    checkEditState();
    this.children.add(child);
    for (LXPoint p : child.points) {
      p.index = this.points.size();
      this.points.add(p);
    }
    return this;
  }

  /**
   * Add a datagram to this model
   *
   * @param datagram Datagram object
   * @return this
   */
  public LXModelBuilder addDatagram(LXDatagram datagram) {
    checkEditState();
    Objects.requireNonNull(datagram, "May not add null LXModelBuilder.addDatagram()");
    this.datagrams.add(datagram);
    return this;
  }

  /**
   * Converts this builder into an index buffer of all its points
   *
   * @return Index buffer of points in this model builder
   */
  public int[] toIndexBuffer() {
    int[] indexBuffer = new int[this.points.size()];
    int i = 0;
    for (LXPoint p : this.points) {
      indexBuffer[i++] = p.index;
    }
    return indexBuffer;
  }

  /**
   * Converts the builder into an immutable model
   *
   * @return LXModel instantiation of this builder
   */
  public LXModel toModel() {
    return new LXModel(this);
  }
}

