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
package optimus.platform.versioning

import optimus.platform._

/**
 * Thrown when there is no transformation path from one shape to another
 */
class NoTransformerPathException(fromShape: Shape, toShape: Seq[Shape])
    extends Exception(s"No transformers found to version from $fromShape to ${toShape.mkString(",")}")

object TransformerConfiguration {

  /**
   * This is the default configuration of all the transformers, which states that transformer will get automatically
   * registered and don't require an explicit TransformerRegistry.register() call.
   *
   * If there is any need to override this default configuration (*should be rare*) ever we can do that by overriding
   * this default configuration within the scope of transformer definition. For e.g. like this,
   *
   * { implicit private val config = TransformerConfiguration.defaultConfig.copy(autoRegister = false)
   * transformer[FromEntity, ToEntity] { ... } }
   */
  implicit val default: TransformerConfiguration = TransformerConfiguration(true)
}

final case class TransformerConfiguration(register: Boolean) {
  def configure(t: Transformer): Unit = {
    if (register) TransformerRegistry.register(t)
  }
}

/*
 * supertype of all Transfomer objects generated by versioning block.
 */
sealed trait Transformer {
  import Transformer._
  val fromRftShape: RftShape
  val toRftShape: RftShape
  final lazy val fromShape: Shape = fromRftShape.generateClientSideShape()
  final lazy val toShape: Shape = toRftShape.generateClientSideShape()
  val operations: Int
  val forwards: Direction
  @async final def apply(f: Properties, temporalContext: TemporalContext): Properties =
    forwards.apply(f, temporalContext)
}

object Transformer {
  type Properties = Map[String, Any]
  type Direction = NodeFunction2[Map[String, Any], TemporalContext, Map[String, Any]]
}

/*
 * for bi-directional transformer blocks
 */
sealed trait Inverse { self: Transformer =>
  import Transformer._
  val inverse: Direction
  val inverseOperations: Int
  @async final def unapply(t: Properties, temporalContext: TemporalContext): Properties =
    inverse.apply(t, temporalContext)
}

object OnewayTransformer {
  def apply(fromRftShape: RftShape, toRftShape: RftShape, forwards: Transformer.Direction, operations: Int = 1)(implicit
      config: TransformerConfiguration): OnewayTransformer = {
    val t = new OnewayTransformer(fromRftShape, toRftShape, forwards, operations)
    config.configure(t)
    t
  }
}

// Will be generated from onewayTransformer {}
final class OnewayTransformer private (
    val fromRftShape: RftShape,
    val toRftShape: RftShape,
    val forwards: Transformer.Direction,
    val operations: Int = 1)
    extends Transformer

object SafeTransformer {
  def apply(
      fromRftShape: RftShape,
      toRftShape: RftShape,
      forwards: Transformer.Direction,
      inverse: Transformer.Direction,
      addedFields: Map[String, Any],
      renamedFields: Map[String, String])(implicit config: TransformerConfiguration): SafeTransformer = {
    val t = new SafeTransformer(fromRftShape, toRftShape, forwards, inverse, addedFields, renamedFields, 1, 1)
    config.configure(t)
    t
  }
}

// Will be generated from transformer {}
final class SafeTransformer private (
    val fromRftShape: RftShape,
    val toRftShape: RftShape,
    val forwards: Transformer.Direction,
    val inverse: Transformer.Direction,
    val addedFields: Map[String, Any],
    val renamedFields: Map[String, String],
    val operations: Int = 1,
    val inverseOperations: Int = 1)
    extends Transformer
    with Inverse

object UnsafeTransformer {
  def apply(
      fromRftShape: RftShape,
      toRftShape: RftShape,
      forwards: Transformer.Direction,
      inverse: Transformer.Direction)(implicit config: TransformerConfiguration): UnsafeTransformer = {
    val t = new UnsafeTransformer(fromRftShape, toRftShape, forwards, inverse, 1, 1)
    config.configure(t)
    t
  }
}

// Will be generated from unsafeTransformer{}{}
final class UnsafeTransformer private (
    val fromRftShape: RftShape,
    val toRftShape: RftShape,
    val forwards: Transformer.Direction,
    val inverse: Transformer.Direction,
    val operations: Int = 1,
    val inverseOperations: Int = 1)
    extends Transformer
    with Inverse

object UnsafeOnewayTransformer {
  def apply(fromRftShape: RftShape, toRftShape: RftShape, forwards: Transformer.Direction, operations: Int = 1)(implicit
      config: TransformerConfiguration): UnsafeOnewayTransformer = {
    val t = new UnsafeOnewayTransformer(fromRftShape, toRftShape, forwards, operations)
    config.configure(t)
    t
  }
}

// Will be generated from unsafeOnewayTransformer{}
final class UnsafeOnewayTransformer private (
    val fromRftShape: RftShape,
    val toRftShape: RftShape,
    val forwards: Transformer.Direction,
    val operations: Int = 1)
    extends Transformer
