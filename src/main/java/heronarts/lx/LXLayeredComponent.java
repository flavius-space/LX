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

package heronarts.lx;

import heronarts.lx.color.LXColor;
import heronarts.lx.color.LXPalette;
import heronarts.lx.model.LXModel;
import heronarts.lx.model.LXPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for system components that run in the engine, which have common
 * attributes, such as parameters, modulators, and layers. For instance,
 * patterns, transitions, and effects are all LXComponents.
 */
public abstract class LXLayeredComponent extends LXModelComponent implements LXLoopTask {

  /**
   * Marker interface for instances which own their own buffer.
   */
  public interface Buffered {}

  private LXBuffer buffer = null;
  private final ModelBuffer myBuffer;

  protected int[] colors = null;

  private final List<LXLayer> mutableLayers = new ArrayList<LXLayer>();
  protected final List<LXLayer> layers = Collections.unmodifiableList(mutableLayers);

  protected final LXPalette palette;

  protected LXLayeredComponent(LX lx) {
    this(lx, null, (LXBuffer) null);
  }

  protected LXLayeredComponent(LX lx, String label) {
    this(lx, label, (LXBuffer) null);
  }

  protected LXLayeredComponent(LX lx, LXDeviceComponent component) {
    this(lx, null, component.getBuffer());
  }

  protected LXLayeredComponent(LX lx, LXBuffer buffer) {
    this(lx, null, buffer);
  }

  protected LXLayeredComponent(LX lx, String label, LXBuffer buffer) {
    super(lx, label);
    if (this instanceof Buffered) {
      if (buffer != null) {
        throw new IllegalArgumentException("Cannot pass existing buffer to LXLayeredComponent.Buffered, has its own");
      }
      buffer = this.myBuffer = new ModelBuffer(lx);
    } else {
      this.myBuffer = null;
    }
    this.palette = lx.engine.palette;
    if (buffer != null) {
      this.buffer = buffer;
      this.colors = buffer.getArray();
    }
  }

  protected LXBuffer getBuffer() {
    return this.buffer;
  }

  public int[] getColors() {
    return getBuffer().getArray();
  }

  protected LXLayeredComponent setBuffer(LXDeviceComponent component) {
    if (this instanceof Buffered) {
      throw new UnsupportedOperationException("Cannot setBuffer on LXLayerdComponent.Buffered, owns its own buffer");
    }
    return setBuffer(component.getBuffer());
  }

  public LXLayeredComponent setBuffer(LXBuffer buffer) {
    this.buffer = buffer;
    this.colors = buffer.getArray();
    return this;
  }

  private LXLayer loopingLayer = null;

  @Override
  public void loop(double deltaMs) {
    long loopStart = System.nanoTime();

    // This protects against subclasses from inappropriately nuking the colors buffer
    // reference. Even if a doofus assigns colors to something else, we'll reset it
    // here on each pass of the loop. Better than subclasses having to call getColors()
    // all the time.
    this.colors = this.buffer.getArray();

    super.loop(deltaMs);
    onLoop(deltaMs);
    for (LXLayer layer : this.mutableLayers) {
      this.loopingLayer = layer;
      layer.setBuffer(this.buffer);

      // TODO(mcslee): is this best here or should it be in addLayer?
      layer.setModel(this.model);

      layer.loop(deltaMs);
    }
    this.loopingLayer = null;
    afterLayers(deltaMs);

    this.timer.loopNanos = System.nanoTime() - loopStart;
  }

  protected /* abstract */ void onLoop(double deltaMs) {}

  protected /* abstract */ void afterLayers(double deltaMs) {}

  private void checkForReentrancy(LXLayer target, String operation) {
    if (this.loopingLayer != null) {
      throw new IllegalStateException(
        "LXLayeredComponent may not modify layers while looping," +
        " component: " + toString() +
        " looping: " + this.loopingLayer.toString(this) +
        " " + operation + ": " + (target != null ? target.toString() : "null")
      );
    }
  }

  protected final LXLayer addLayer(LXLayer layer) {
    if (layer == null) {
      throw new IllegalArgumentException("Cannot add null layer");
    }
    checkForReentrancy(layer, "add");
    if (this.mutableLayers.contains(layer)) {
      throw new IllegalStateException("Cannot add layer twice: " + this + " " + layer);
    }
    layer.setParent(this);
    this.mutableLayers.add(layer);
    return layer;
  }

  protected final LXLayer removeLayer(LXLayer layer) {
    checkForReentrancy(layer, "remove");
    this.mutableLayers.remove(layer);
    layer.dispose();
    return layer;
  }

  public final List<LXLayer> getLayers() {
    return this.layers;
  }

  @Override
  public void dispose() {
    checkForReentrancy(null, "dispose");
    for (LXLayer layer : this.mutableLayers) {
      layer.dispose();
    }
    this.mutableLayers.clear();
    if (this.myBuffer != null) {
      this.myBuffer.dispose();
    }
    super.dispose();
  }

  /**
   * Sets the color of point i
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(int i, int c) {
    this.colors[i] = c;
    return this;
  }

  /**
   * Blend the color at index i with its existing value
   *
   * @param i Index
   * @param c New color
   * @param blendMode blending mode
   *
   * @return this
   */
  protected final LXLayeredComponent blendColor(int i, int c, LXColor.Blend blendMode) {
    this.colors[i] = LXColor.blend(this.colors[i], c, blendMode);
    return this;
  }

  protected final LXLayeredComponent blendColor(LXModel model, int c, LXColor.Blend blendMode) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = LXColor.blend(this.colors[p.index], c, blendMode);
    }
    return this;
  }

  /**
   * Adds to the color of point i, using blendColor with ADD
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent addColor(int i, int c) {
    this.colors[i] = LXColor.add(this.colors[i], c);
    return this;
  }

  /**
   * Adds to the color of point (x,y) in a default GridModel, using blendColor
   *
   * @param x x-index
   * @param y y-index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent addColor(int x, int y, int c) {
    return addColor(x + y * this.lx.width, c);
  }

  /**
   * Adds the color to the fixture
   *
   * @param model model
   * @param c New color
   * @return this
   */
  protected final LXLayeredComponent addColor(LXModel model, int c) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = LXColor.add(this.colors[p.index], c);
    }
    return this;
  }

  /**
   * Subtracts from the color of point i, using blendColor with SUBTRACT
   *
   * @param i Point index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent subtractColor(int i, int c) {
    this.colors[i] = LXColor.subtract(this.colors[i], c);
    return this;
  }

  /**
   * Sets the color of point (x,y) in a default GridModel
   *
   * @param x x-index
   * @param y y-index
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(int x, int y, int c) {
    this.colors[x + y * this.lx.width] = c;
    return this;
  }

  /**
   * Gets the color at point (x,y) in a GridModel
   *
   * @param x x-index
   * @param y y-index
   * @return Color value
   */
  protected final int getColor(int x, int y) {
    return this.colors[x + y * this.lx.width];
  }

  /**
   * Sets all points to one color
   *
   * @param c Color
   * @return this
   */
  protected final LXLayeredComponent setColors(int c) {
    for (int i = 0; i < colors.length; ++i) {
      this.colors[i] = c;
    }
    return this;
  }

  /**
   * Sets the color of all points in a fixture
   *
   * @param model Model
   * @param c color
   * @return this
   */
  protected final LXLayeredComponent setColor(LXModel model, int c) {
    for (LXPoint p : model.points) {
      this.colors[p.index] = c;
    }
    return this;
  }

  /**
   * Clears all colors
   *
   * @return this
   */
  protected final LXLayeredComponent clearColors() {
    return setColors(0);
  }

}
