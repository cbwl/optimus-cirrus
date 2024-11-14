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
package optimus.examples.platform01.data

import optimus.platform._
import optimus.examples.platform.entities.SimpleEvent
import optimus.examples.platform.entities.SimpleEntity

object Events extends LegacyOptimusApp[SupplyEntityNameArgs] {
  // Purge the user database
  DAL.purgePrivateContext()

  override def setup(): Unit = {
    if (cmdLine.uri == null)
      cmdLine.uri = "broker://dev?context=named&context.name=" + System.getProperty("user.name")
  }

  val simpleEvent = SimpleEvent.uniqueInstance(cmdLine.name)
  println("simpleEvent: " + simpleEvent)

  val simpleEntity = SimpleEntity(cmdLine.name)
  println("simpleEntity: " + simpleEntity)

  val storedEvent = newTransaction {
    newEvent(simpleEvent) {
      DAL.put(simpleEntity)
    }
  }

  println("storedEvent: " + storedEvent)

  given(validTimeAndTransactionTimeNow) {
    val retrievedEvent = SimpleEvent.get(cmdLine.name)
    println("retrievedEvent: " + retrievedEvent + " with entities " + retrievedEvent.entities.mkString(","))
  }
}
