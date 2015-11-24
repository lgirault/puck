package puck.gui

import java.io.{File, PipedInputStream, PipedOutputStream}

import puck.LoadingListener
import puck.graph._
import puck.graph.io._

import puck.gui.explorer.{SetTopLevelVisible, AccessGraphModified}
import puck.gui.imageDisplay.ImageFrame
import puck.gui.svg.SVGFrame
import puck.search.Search
import puck.util.{PuckLogger, PuckLog}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.swing.event.Event
import scala.swing.{Component, ProgressBar, Publisher}
import scala.util.{Failure, Success}


sealed trait ControlRequest extends Event

case class LoadCodeRequest() extends ControlRequest
case class LoadConstraintRequest() extends ControlRequest
case class GraphDisplayRequest
(title : String,
 graph : DependencyGraph,
 printId : Boolean,
 printSignature : Boolean,
 visibility : VisibilitySet.T,
 sUse : Option[DGUses] = None,
 format : DotOutputFormat = Svg)
 extends ControlRequest

case class ConstraintDisplayRequest(graph : DependencyGraph) extends ControlRequest
//case class ExploreRequest
//(builder : ConstraintSolvingSearchEngineBuilder)
//  extends ControlRequest

//case class SearchStateMapPrintingRequest
//(stateMap : Map[Int, Seq[SearchState[SResult]]],
// printId : Boolean,
// printSignature : Boolean,
// visibility : VisibilitySet.T)
//  extends ControlRequest

//case class SearchStateSeqPrintingRequest
//(subDir : String,
// states : Seq[SearchState[SResult]],
// sPrinter : Option[SearchState[SResult] => String],
// printId : Boolean,
// printSignature : Boolean,
// visibility : VisibilitySet.T)
//  extends ControlRequest

case class PrintConstraintRequest() extends ControlRequest
case class ApplyOnCodeRequest(searchResult : DependencyGraph) extends ControlRequest

sealed abstract class Answer extends Event
case class ExplorationFinished(result : Search[SResult]) extends Answer


class PuckControl(logger0 : PuckLogger,
                  val filesHandler : FilesHandler,
                  val graphUtils: GraphUtils,
                  private val progressBar : ProgressBar,
                  private val delayedDisplay : ArrayBuffer[Component])
  extends Publisher {

  private implicit val logger : PuckLogger = logger0

  type GraphT = DependencyGraph
  import PuckLog.defaultVerbosity

  var dg2AST : DG2AST = _

  var displayNameSpaceOnlyDefaultThreshold = 150

  def loadCode( onSuccess : => Unit) = Future {
    progressBar.visible = true
    progressBar.value = 0

    dg2AST = filesHandler.loadGraph(Some(new LoadingListener {
      override def update(loading: Double): Unit =
        progressBar.value = (loading * 100).toInt
    }))
    progressBar.visible = false
    publish(AccessGraphModified(dg2AST.initialGraph))


    if(dg2AST.initialGraph.nodesId.size > displayNameSpaceOnlyDefaultThreshold) {
//      import graphUtils.nodeKindKnowledge.kindOfKindType
//      dg2AST.initialGraph.content(dg2AST.initialGraph.rootId)
//      publish(SetVisibleFromKind(kindOfKindType(NameSpace)))
      publish(SetTopLevelVisible)
      logger.writeln(s"Graph have more than $displayNameSpaceOnlyDefaultThreshold, " +
        s"top level visibility selected by default.")
        //s"namespace visibility selected by default.")
    }

    delayedDisplay.foreach(_.visible = true)
  } onComplete {
    case Success(_) => onSuccess
    case Failure(exc) =>
      progressBar.visible = false
      exc.printStackTrace()
  }


//  def explore (trace : Boolean = false,
//               builder : ConstraintSolvingSearchEngineBuilder,
//               automaticConstraintLoosening: Boolean) : Search[DependencyGraph] = {
//
//    val engine = builder(dg2AST.initialGraph, automaticConstraintLoosening)
//
//    puck.util.Time.time(logger, defaultVerbosity) {
//      engine.explore()
//    }
//
//    engine
//  }

  def loadConstraints() : Unit = {
    try {
      logger.writeln("Loading constraints ...")
      dg2AST = filesHandler.parseConstraints(dg2AST)
      logger.writeln(" done:")
      dg2AST.initialGraph.printConstraints(logger, defaultVerbosity)
    }
    catch {
      case _ : java.io.FileNotFoundException => logger writeln "constraint file not found"
      case e: Error => logger writeln e.getMessage
    }
  }

  def displayGraph(title : String,
                   graph : GraphT,
                   opts : PrintingOptions) : DotOutputFormat => Unit = { format =>
    logger.writeln("Printing graph ...")

    val pipedOutput = new PipedOutputStream()
    val pipedInput = new PipedInputStream(pipedOutput)


    format match {
      case Png =>
        Future {
          val imgframe = ImageFrame(pipedInput)
          imgframe.title = title
        }

      case Svg =>
        Future {
          logger.writeln("requesting svg frame")
          new SVGFrame(pipedInput, opts, filesHandler, graphUtils, dg2AST){
            this.setTitle(title)
            logger.writeln("sploosh")
          }

          logger.writeln("bloop")
        }
     }

    DotPrinter.genImage(graph, graphUtils.dotHelper, opts, format, pipedOutput) {
      case Success(i) if i == 0 => logger.writeln("success")
      case _ => logger.writeln("fail")
    }
  }

  def applyOnCode(record : DependencyGraph) : Unit = {
    Future {
      logger.write("generating code ...")
      dg2AST(record)
      dg2AST.printCode(filesHandler.outDirectory !)
      logger.writeln(" done")
    } onComplete {
      case Success(_) => ()
      case Failure(exc) => exc.printStackTrace()
    }
  }

//  type StateT = SearchState[DependencyGraph]
//  def printStateSeq( subDirStr : String,
//                     states : Seq[StateT],
//                     sPrinter : Option[StateT => String],
//                     printId : Boolean,
//                     printSignature : Boolean,
//                     visibility : VisibilitySet.T): Unit ={
//    val d = filesHandler.graphFile("_results")
//    d.mkdir()
//    val subDir = filesHandler.graphFile("_results%c%s".format(File.separatorChar, subDirStr))
//    subDir.mkdir()
//    filesHandler.printCSSearchStatesGraph(subDir, states, graphUtils.dotHelper, visibility, sPrinter, printId, printSignature)
//  }
//
//  def showStateSeq(states : Seq[StateT],
//                   printId : Boolean,
//                   printSignature : Boolean,
//                   visibility : VisibilitySet.T): Unit = {
//    Future(new ImageExplorer(filesHandler, graphUtils.dotHelper, logger, states.toIndexedSeq, visibility, printId, printSignature))
//    ()
//  }

  reactions += {
    case LoadCodeRequest() => loadCode(loadConstraints())

    case LoadConstraintRequest() => loadConstraints()

    case GraphDisplayRequest(title, graph, printId, printSignature, visibility, sUse, format) =>
      displayGraph(title, graph,
        PrintingOptions(visibility, printId, printSignature, sUse))(format)

    case ConstraintDisplayRequest(graph) =>
      graph.printConstraints(logger, defaultVerbosity)

    case ApplyOnCodeRequest(searchResult) => applyOnCode(searchResult)

//    case ExploreRequest(builder) =>
//
//      val engine = builder(dg2AST.initialGraph,
//                          automaticConstraintLoosening = true)
//
//      Future {
//        logger.writeln("Solving constraints ...")
//        puck.util.Time.time(logger, defaultVerbosity) {
//          engine.explore()
//        }
//        engine
//      } onComplete {
//        case Success(res) =>
//          logger.writeln("Solving done")
//          publish(ExplorationFinished(res))
//        case Failure(exc) =>
//          logger.writeln("Solving failure")
//          exc.printStackTrace()
//          publish(ExplorationFinished(engine))
//          //filesHandler.logger writeln exc.getStackTrace.mkString("\n")
//      }

//    case SearchStateMapPrintingRequest(stateMap, printId, printSignature, visibility) =>
//      filesHandler.printCSSearchStatesGraph(stateMap, graphUtils.dotHelper, visibility, printId, printSignature)
//
//    case SearchStateSeqPrintingRequest(subDir, states, sPrinter, printId, printSignature, visibility) =>
//     // printStateSeq(subDir, states, sPrinter, printId, printSignature)
//      logger.writeln("history request")
//      showStateSeq(states, printId, printSignature, visibility)
  }

}