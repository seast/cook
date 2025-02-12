package cook.config.runner.buildin

import scala.collection.mutable.HashMap

import java.io.File

import org.apache.tools.ant.DirectoryScanner

import cook.config.runner.EvalException
import cook.config.runner.value._
import cook.util._

/**
 * Buildin function glob.
 *
 * return filelist: ListValue
 *
 * Example:
 *
 * glob(["*.java"])
 */
object Glob extends BuildinFunction("glob", GlobArgsDef()) {

  override def eval(path: String, argsValue: Scope): Value = {
    var l = argsValue("filters")
    if (l.typeName != "List") {
      l = ListValue(l.name, Seq(l))
    }
    val filters = l.toListStr("Buildin function \"glob\" only aceppt string or string list")

    val baseDir = argsValue("baseDir")
    val baseDirFile = if (baseDir.isNull) {
      FileUtil(path)
    } else {
      FileUtil(baseDir.toStr)
    }

    val dirScanner = new DirectoryScanner
    dirScanner.setIncludes(filters.toArray)
    dirScanner.setBasedir(baseDirFile)
    dirScanner.scan

    val files = dirScanner.getIncludedFiles

    ListValue("glob()", files.map((f) => {
        FileLabelValue("<unknown>", new FileLabel(baseDirFile.getAbsolutePath, f))
    }))
  }
}

object GlobArgsDef {

  def apply() = {
    val names = Seq[String]("filters", "baseDir")
    val defaultValues = new HashMap[String, Value]
    defaultValues("baseDir") = NullValue("baseDir")
    new ArgsDef(names, defaultValues)
  }
}
