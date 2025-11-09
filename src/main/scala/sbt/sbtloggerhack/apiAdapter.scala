/*
 * Copyright 2013-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

package sbt.sbtloggerhack

import com.github.sideeffffect.sbtlogger.{GHACompilerReporter, GHALogAppender, GHALogger, GHALoggerAppender}
import com.github.sideeffffect.sbtlogger.GHACompilerReporter.FilePosition
import sbt.{Def, Reference, Scope, Select, Zero}
import xsbti.Problem

import scala.collection.mutable

object apiAdapter {

  type SessionSettings = sbt.internal.SessionSettings
  type ExtraLogger = org.apache.logging.log4j.core.Appender

  def projectScope(project: Reference): Scope = Scope(Select(project), Zero, Zero, Zero)

  def extraLogger(
      tcLoggers: mutable.Map[String, GHALogger],
      tcLogAppender: GHALogAppender,
      scope: String,
  ): ExtraLogger = {
    val appender = new GHALoggerAppender(tcLogAppender, scope)
    appender.start()
    appender
  }

  def reporterSettings(tcLogAppender: GHALogAppender): Def.Setting[_] = {
    import sbt.Keys.compile
    Unhide.compilerReporter in compile := {
      val defaultReporter = (Unhide.compilerReporter in compile).value
      new GHACompilerReporter(defaultReporter)
    }
  }

  def toFilePosition(position: xsbti.Position): Option[FilePosition] = {
    val path = position.sourcePath()
    val maybeStartLine = position.startLine()
    val maybeEndLine = position.endLine()
    val startLine = if (maybeStartLine.isPresent) maybeStartLine.get().intValue() else 0
    val endLine = if (maybeEndLine.isPresent) maybeEndLine.get().intValue() else 0
    if (path.isPresent) Some(FilePosition(path.get(), startLine, endLine))
    else None
  }

  abstract class ReporterAdapter(delegate: xsbti.Reporter) extends xsbti.Reporter {
    def delegateLog(problem: Problem): Unit = {
      delegate.log(problem)
    }
  }
}
