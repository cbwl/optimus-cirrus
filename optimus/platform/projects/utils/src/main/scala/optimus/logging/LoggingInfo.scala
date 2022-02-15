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
package optimus.logging

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.asScalaIteratorConverter
import java.net.InetAddress
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.concurrent.TimeUnit

import optimus.platform.util.Log
import org.apache.commons.io.FileUtils

import scala.util.Properties
import scala.util.control.NonFatal

object Pid extends Log {

  private def complain(e: Throwable) = {
    log.warn(
      "Unable to resolve current process id (pid) for troubleshooting or monitoring. Please fix immediately. Exception was {} '{}'.",
      e.getClass().getCanonicalName(),
      e.getMessage()
    )
    None
  }
  // TODO (OPTIMUS-47224): This should really return Option[Long], but we need to ensure it doesn't break
  // DAL code.
  val pidInt: Option[Int] = try {
    Some(ProcessHandle.current().pid().toInt)
  } catch {
    case NonFatal(e) => complain(e)
  }

  val pid: Option[Long] = try {
    Some(ProcessHandle.current().pid)
  } catch {
    case NonFatal(e) => complain(e)
  }

  def pidOrZero(): Long = pid.getOrElse(0);
}


object LoggingInfo {
  def getLogFile: String = {
    org.slf4j.LoggerFactory.getILoggerFactory() match {
      case lc: ch.qos.logback.classic.LoggerContext =>
        val loggers = lc.getLoggerList.asScala
        loggers.flatMap(getLogFiles(_)).mkString(",")
      case x =>
        "unknown_context"
    }
  }

  def getLogFiles(log: ch.qos.logback.core.spi.AppenderAttachable[_]): Seq[String] = {
    val appenders = log.iteratorForAppenders().asScala
    val files = appenders.flatMap {
      case a: ch.qos.logback.core.spi.AppenderAttachable[_] => getLogFiles(a)
      case a: ch.qos.logback.core.FileAppender[_]           => Seq(a.getFile)
      case a: ch.qos.logback.core.ConsoleAppender[_]        => Seq("console")
      case a                                                => Seq(a.toString)
    }
    files.toSeq
  }

  lazy val getHostInetAddr: InetAddress = InetAddress.getLocalHost
  lazy val getHost: String = getHostInetAddr.getHostName
  lazy val getUser: String = System.getProperty("user.name", "unknown")
  lazy val pid: Long = Pid.pidOrZero

  def tmpLog(prefix: String):Path =  Paths.get(System.getProperty("java.io.tmpdir"), s"$prefix-$getHost-${ Pid.pidOrZero }-${ Instant.now }.log")

  class Uploader(url: String, pathParam: String = "files=@%s") extends Log {

    def upload(prefix: String, msg: String): Boolean = {
      val path: Path = tmpLog(prefix)
      val wrote = try {
        FileUtils.write(path.toFile, msg, Charset.defaultCharset())
        true
      } catch {
        case NonFatal(e) =>
          log.warn(s"Unable to write to $path: $e")
          false
      }
      wrote && upload(path)
    }

    def upload(path: Path): Boolean = {
      if (Properties.isWin)
        false
      else
        try {
          val params = String.format(pathParam, path)
          val builder = new ProcessBuilder("/usr/bin/curl", "-X", "POST", url, "-F", params)
          val process = builder.start()
          process.waitFor(10, TimeUnit.SECONDS)
          true
        } catch {
          case NonFatal(e) =>
            log.warn(s"Unable to send $path to $url: $e")
            false
        }
    }
  }
}
