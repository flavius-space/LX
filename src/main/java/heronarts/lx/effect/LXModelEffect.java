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

package heronarts.lx.effect;

import heronarts.lx.LX;
import heronarts.lx.LXModelComponent;
import heronarts.lx.model.LXModel;

public abstract class LXModelEffect<T extends LXModel> extends LXEffect {

  protected T model;

  @SuppressWarnings("unchecked")
  protected LXModelEffect(LX lx) {
    super(lx);
    this.model = (T) lx.getModel();
  }

  @Override
  public T getModel() {
    return this.model;
  }

  @Override
  @SuppressWarnings("unchecked")
  public LXModelComponent setModel(LXModel model) {
    this.model = (T) model;
    super.setModel(model);
    return this;
  }

}
