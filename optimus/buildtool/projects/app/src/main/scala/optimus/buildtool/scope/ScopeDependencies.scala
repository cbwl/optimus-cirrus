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
package optimus.buildtool.scope

import optimus.buildtool.app.CompilationNodeFactory
import optimus.buildtool.artifacts.Artifact
import optimus.buildtool.artifacts.ArtifactType.CompileOnlyResolution
import optimus.buildtool.artifacts.ArtifactType.CompileResolution
import optimus.buildtool.artifacts.ArtifactType.RuntimeResolution
import optimus.buildtool.artifacts.ExternalClassFileArtifact
import optimus.buildtool.artifacts.FingerprintArtifact
import optimus.buildtool.artifacts.InternalArtifactId
import optimus.buildtool.artifacts.ResolutionArtifact
import optimus.buildtool.artifacts.ResolutionArtifactType
import optimus.buildtool.cache.ArtifactCache
import optimus.buildtool.config.Dependencies
import optimus.buildtool.config.DependencyDefinition
import optimus.buildtool.config.DependencyDefinitions
import optimus.buildtool.config.ForbiddenDependencyConfiguration
import optimus.buildtool.config.HasScopeId
import optimus.buildtool.config.NativeDependencyDefinition
import optimus.buildtool.config.ScopeId
import optimus.buildtool.config.Substitution
import optimus.buildtool.files.Asset
import optimus.buildtool.resolvers.ExternalDependencyResolver
import optimus.buildtool.trace.ObtTrace
import optimus.buildtool.utils.CompilePathBuilder
import optimus.buildtool.utils.Utils.{distinctLast, distinctLastBy}
import optimus.platform._

import scala.collection.compat._
import scala.collection.immutable.Seq

@entity class ScopeDependencies(
    val id: ScopeId,
    val mavenOnly: Boolean,
    skipDependencyMappingValidation: Boolean,
    val dependencies: Dependencies,
    externalNativeDependencies: Seq[NativeDependencyDefinition],
    substitutions: Seq[Substitution],
    forbiddenDependencies: Seq[ForbiddenDependencyConfiguration],
    val tpe: ResolutionArtifactType,
    pathBuilder: CompilePathBuilder,
    externalDependencyResolver: ExternalDependencyResolver,
    scopedCompilationFactory: CompilationNodeFactory,
    cache: ArtifactCache,
    hasher: FingerprintHasher
) extends HasScopeId {

  private def isMavenCompatible: Boolean =
    mavenOnly || (externalNativeDependencies.isEmpty && dependencies.hasMavenLibsOrEmpty)

  @node def directScopeDependencies: Seq[CompilationNode] = {
    internalDependencyIds.distinct
      .sortBy(_.toString)
      .apar
      .flatMap(scopedCompilationFactory.lookupScope)
  }

  @node def transitiveScopeDependencies: Seq[CompilationNode] =
    distinctLastBy(
      directScopeDependencies.apar
        .flatMap { d =>
          d +: ScopeDependencies.dependencies(tpe, d).transitiveScopeDependencies
        })(_.id)

  @node def transitiveExternalDependencyIds: DependencyDefinitions = {
    val upstreamExtDeps = {
      transitiveScopeDependencies.apar.flatMap { dep =>
        val currentUpstream = ScopeDependencies.dependencies(tpe, dep)
        // use predefined mavenLibs=[] from maven compatible upstream module when downstream module is maven only
        val forMavenDownstream = mavenOnly && currentUpstream.isMavenCompatible
        currentUpstream.externalDependencyIds(forMavenDownstream)
      }
    }
    DependencyDefinitions(
      directIds = distinctLast(externalDependencyIds()),
      indirectIds = distinctLast(upstreamExtDeps),
      substitutions = substitutions,
      forbiddenDependencies = forbiddenDependencies,
      skipDependencyMappingValidation = skipDependencyMappingValidation
    )
  }

  @node def transitiveInternalDependencyIds: Seq[ScopeId] =
    transitiveScopeDependencies.apar.flatMap(ScopeDependencies.dependencies(tpe, _).internalDependencyIds)

  @node def transitiveInternalDependencyIdsAll: Seq[ScopeId] =
    (transitiveInternalDependencyIds ++ internalDependencyIds).distinct

  /** All JNI paths, both from our explicit declaration of native dependencies and from ivy files. */
  @node def transitiveJniPaths: Seq[String] =
    resolution.map(_.result.jniPaths).getOrElse(Nil) ++ transitiveNativeDependencies.flatMap(_.paths)

  @node def transitiveExtraFiles: Seq[Asset] = transitiveNativeDependencies.flatMap(_.extraPaths)

  @node private def transitiveNativeDependencies: Seq[NativeDependencyDefinition] =
    (externalNativeDependencies
      ++ transitiveScopeDependencies.apar.flatMap(_.runtimeDependencies.transitiveNativeDependencies)).distinct

  @node def externalInputsHash: FingerprintArtifact = {
    hasher.hashFingerprint(
      externalDependencyResolver.fingerprintDependencies(transitiveExternalDependencyIds),
      tpe.fingerprintType
    )
  }

  @node def transitiveExternalArtifacts: Seq[Artifact] = resolution.to(Seq) ++
    (if (ScopedCompilation.generate(tpe.fingerprintType)) Some(externalInputsHash) else None)

  @node def resolution: Option[ResolutionArtifact] =
    if (ScopedCompilation.generate(tpe)) {
      val fingerprintHash = externalInputsHash
      cache.getOrCompute[ResolutionArtifactType](id, tpe, None, fingerprintHash.hash) {
        ObtTrace.traceTask(id, tpe.category) {
          val tpeStr = tpe.name.replace('-', ' ')
          log.info(s"[$id] Starting $tpeStr")
          val resolved = externalDependencyResolver.resolveDependencies(transitiveExternalDependencyIds)

          val artifact = ResolutionArtifact.create(
            InternalArtifactId(id, tpe, None),
            resolved,
            pathBuilder.outputPathFor(id, fingerprintHash.hash, tpe, None).asJson,
            tpe.category)
          if (!artifact.messages.exists(_.isError)) artifact.storeJson()
          log.info(s"[$id] Completed $tpeStr")
          Some(artifact)
        }
      }
    } else None

  @node def transitiveExternalDependencies: Seq[ExternalClassFileArtifact] =
    resolution.map(_.result.resolvedArtifacts).getOrElse(Nil)

  @node def internalDependencyIds: Seq[ScopeId] = dependencies.modules

  /**
   * be used for fingerprint calculation or validator only
   * @return all predefined external dependencies in obt file (libs and mavenLibs)
   */
  @node def dualExternalDependencyIds: Seq[DependencyDefinition] = dependencies.dualExternalDeps

  @node def externalDependencyIds(forMavenDownstream: Boolean = false): Seq[DependencyDefinition] =
    dependencies.externalDeps(forMavenDownstream)

  override def toString: String = s"ScopeDependencies($id)"
}

object ScopeDependencies {
  private def dependencies(tpe: ResolutionArtifactType, scope: CompilationNode): ScopeDependencies =
    tpe match {
      // for CompileOnly, we want the transitive Compile deps NOT the transitive CompileOnly deps
      case CompileResolution | CompileOnlyResolution => scope.upstream.compileDependencies
      case RuntimeResolution                         => scope.runtimeDependencies
    }

  import optimus.buildtool.cache.NodeCaching.reallyBigCache

  // avoiding resolving external dependencies multiple times as this could make our build slower than necessary
  resolution.setCustomCache(reallyBigCache)
}
