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
package optimus.buildtool.cache.silverking

import optimus.buildtool.cache.silverking.SilverKingConfig.{ServiceLookup, Explicit}

sealed trait ClusterType
object ClusterType {

  def forLookup(config: SilverKingConfig): ClusterType = {
    config match {
      case sl: ServiceLookup if sl.name == "obtqa"  => ClusterType.QA
      case sl: ServiceLookup if sl.name == "obtdev" => ClusterType.Dev
      case Explicit(name, _, _, _)                  => ClusterType.Labeled(name)
      case _                                        => ClusterType.Custom
    }
  }

  case object QA extends ClusterType
  case object Dev extends ClusterType
  final case class Labeled(name: String) extends ClusterType {
    override def toString = name
  }
  case object Custom extends ClusterType
}
