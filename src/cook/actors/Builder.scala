package cook.actors

import java.util.concurrent.locks.ReentrantLock

import scala.actors.{Actor, Exit}
import scala.actors.Actor._
import scala.actors.Actor.State._
import scala.annotation.tailrec
import scala.collection.mutable.HashSet
import scala.collection.mutable.Queue

import cook.app.config.Config
import cook.app.console.CookConsole
import cook.target._
import cook.util._

case class Cached(targetName: String)
case class Built(targetName: String)

class BuildActor(val targetName: String, controlActor: ControlActor) extends Actor {

  def act {
    link(controlActor)

    val target = TargetManager.getTarget(targetName)
    target.build

    if (target.isCached) {
      controlActor ! Cached(targetName)
    } else {
      controlActor ! Built(targetName)
    }

    unlink(controlActor)
  }
}

class ControlActor(analyst: Analyst) extends Actor {

  val lock = new ReentrantLock
  val finish = lock.newCondition

  def act {
    trapExit = true
    CookConsole.mark('buildStatus)

    receive {
      case 'BuildThemAll =>
        tryStartMoreBuildActor

        while (!analyst.isFinished) {
          receive {
            case Cached(targetName) =>
              analyst.setCached(targetName)
              updateBuildStatus(analyst)
              tryStartMoreBuildActor
            case Built(targetName) =>
              analyst.setBuilt(targetName)
              updateBuildStatus(analyst)
              tryStartMoreBuildActor
            case Exit(from, reason) =>
              exit(reason)
          }
        }

        reply(0)
    }
  }

  @tailrec
  private def tryStartMoreBuildActor {
    if ((analyst.available > 0) && (analyst.building.size < Config.parallel)) {
      val targetName = analyst.get.get
      analyst.setBuilding(targetName)

      val buildActor = new BuildActor(targetName, this)
      buildActor.start
      updateBuildStatus(analyst)
      tryStartMoreBuildActor
    }
  }

  def updateBuildStatus(analyst: Analyst) {
    CookConsole.clearToMark('buildStatus)

    CookConsole.cookPercentage(analyst.percentage)

    if (!analyst.isFinished) {
      CookConsole.print("Building ")
      CookConsole.control(Console.CYAN)
      CookConsole.print("%d", analyst.building.size)
      CookConsole.reset
      CookConsole.print(", ")
    }
    CookConsole.print("Cached ")
    CookConsole.control(Console.CYAN)
    CookConsole.print("%d", analyst.cached.size)
    CookConsole.reset
    CookConsole.print(", Built ")
    CookConsole.control(Console.CYAN)
    CookConsole.print("%d", analyst.built.size)
    CookConsole.reset

    val rest = analyst.total - analyst.building.size - analyst.cached.size - analyst.built.size
    if (rest > 0) {
      CookConsole.print(", Rest ")
      CookConsole.control(Console.CYAN)
      CookConsole.print("%d", rest)
      CookConsole.reset
    }

    if (analyst.building.nonEmpty) {
      CookConsole.println(":")
      CookConsole.control(Console.GREEN)
      CookConsole.println(analyst.building.mkString(", "))
      CookConsole.reset
    }
  }
}

object Builder {

  def build(targetLabels: Seq[TargetLabel]): Int = {
    val analyst = Analyst(targetLabels: _*)
    val controlActor = new ControlActor(analyst)
    controlActor.start
    (controlActor !? 'BuildThemAll).asInstanceOf[Int]
  }

}
