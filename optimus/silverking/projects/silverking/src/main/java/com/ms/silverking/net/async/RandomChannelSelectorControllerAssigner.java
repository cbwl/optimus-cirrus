/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License").
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ms.silverking.net.async;

import java.nio.channels.SelectableChannel;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** */
public class RandomChannelSelectorControllerAssigner<T extends Connection>
    implements ChannelSelectorControllerAssigner<T> {
  public RandomChannelSelectorControllerAssigner() {}

  @Override
  /**
   * Assigns, the given channel to a SelectorController chosen from the list. Does *not* add the
   * channel to the SelectorController.
   */
  public SelectorController<T> assignChannelToSelectorController(
      SelectableChannel channel, List<SelectorController<T>> selectorControllers) {
    int index;

    index = ThreadLocalRandom.current().nextInt(selectorControllers.size());
    return selectorControllers.get(index);
  }
}
