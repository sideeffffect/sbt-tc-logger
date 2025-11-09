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

package com.github.sideeffffect.sbtlogger

import com.github.sideeffffect.sbtlogger.GHACompilerReporter.inspectionMessage
import sbt.sbtloggerhack.apiAdapter.{ReporterAdapter, toFilePosition}
import xsbti.{Position, Problem}

class GHACompilerReporter(delegate: xsbti.Reporter) extends ReporterAdapter(delegate) {

  override def reset(): Unit = delegate.reset()
  override def hasErrors: Boolean = delegate.hasErrors
  override def hasWarnings: Boolean = delegate.hasWarnings
  override def printSummary(): Unit = delegate.printSummary()
  override def problems(): Array[Problem] = delegate.problems()
  override def comment(pos: Position, msg: String): Unit = delegate.comment(pos, msg)

  override def log(problem: Problem): Unit = {
    logInspection(problem)
    delegateLog(problem)
  }

  def logInspection(problem: Problem): Unit = {
    inspectionMessage(problem).foreach { msg =>
      println(msg.toMessageString)
    }
  }
}

object GHACompilerReporter {

  case class FilePosition(sourcePath: String, startLine: Int, endLine: Int)

  case class ServerMessage(
      severity: String,
      filePosition: FilePosition,
      title: String,
      message: String,
  ) {
    def toMessageString: String = {
      s"::$severity file=${filePosition.sourcePath.replaceFirst("\\$\\{BASE}/", "").replace(",", "")},line=${filePosition.startLine},endLine=${filePosition.endLine},title=${title.replace("::", "")}::${message.replace("\n", " ")}"
    }
  }

  def inspectionMessage(problem: Problem): Option[ServerMessage] = {
    val maybeFilePosition = toFilePosition(problem.position())

    maybeFilePosition.map { filePosition =>
      ServerMessage(inspectionSeverity(problem.severity()), filePosition, problem.category(), problem.message())
    }
  }

  private def inspectionSeverity(severity: xsbti.Severity): String = {
    import xsbti.Severity.*
    severity match {
      case Info  => "notice"
      case Warn  => "warning"
      case Error => "error"
    }
  }
}
