package puck.graph.io

import java.io._


import puck.graph._
import puck.search.{Search, SearchState, SearchEngine}
import puck.util._

import scala.sys.process.Process
import scala.util.Try

trait ConstraintSolvingSearchEngineBuilder[Kind <: NodeKind[Kind], T] {
  def apply(graph : AccessGraph[Kind, T]) :
  SearchEngine[ResultT[Kind, T]]
}

/**
 * Created by lorilan on 13/08/14.
 */
object FilesHandler{
  object Default{
    final val srcDirName : String = "src"
    final val outDirName : String = "out"
    final val decoupleFileName: String = "decouple.pl"
    final val graphFileName: String = "graph"
    final val jarListFileName: String = "jar.list"
    final val apiNodesFileName: String = "api_nodes"
    final val logFileName: String = outDirName + File.separator + "graph_solving.log"
  }
}

/*trait ConstraintSolvingSearchEngineBuilder[Kind <: NodeKind[Kind]] {
  def apply(graph : AccessGraph[Kind]) :
  SearchEngine[Recording[Kind]]
}*/

abstract class FilesHandler[Kind <: NodeKind[Kind], T](workingDirectory : File){

  private [this] var srcDir0 : Option[File] = None
  private [this] var outDir0 : Option[File] = None
  private [this] var jarListFile0 : Option[File] = None
  private [this] var apiNodesFile0 : Option[File] = None
  private [this] var decouple0 : Option[File] = None
  private [this] var logFile0 : Option[File] = None


  var graphStubFileName : String = FilesHandler.Default.graphFileName

  import PuckLog.defaultVerbosity

  val logPolicy : PuckLog.Verbosity => Boolean = {
/*    case (PuckLog.Search,_) | (PuckLog.Solver, _) => true
    case (PuckLog.InGraph,_) | (PuckLog.InJavaGraph, _ ) => true*/
    case (PuckLog.NoSpecialContext, _) => true
    case _ => false

  }

  def logger : PuckLogger = logger0
  def logger_=( l : PuckLogger){logger0 = l}

  type GraphT = AccessGraph[Kind, T]

  private [this] var ag : GraphT = _
  def graph = ag
  protected def graph_=(g : GraphT){ ag = g }

  var graphBuilder : GraphBuilder[Kind, T] = _

  def setCanonicalOptionFile(prev : Option[File], sf : Option[File]) = {
    sf match {
      case None => prev
      case Some(f) => val fc = f.getCanonicalFile
        if(fc.exists())
          Some(fc)
        else {
          logger.writeln("%s does not exists.".format(f))
          None
        }
    }
  }


  def setWorkingDirectory(dir : File){
    this.srcDir0 = setCanonicalOptionFile(this.srcDir0, Some(dir))
    this.srcDir0 match {
      case None => throw new AGError("Invalid working directory !!!")
      case Some(d) =>

        def defaultFile(fileName: String) =
          Some(new File( d + File.separator + fileName))

        import FilesHandler.Default

        val Some(od) = defaultFile(Default.outDirName)
        if(!od.exists()){
          od.mkdir()
        }
        outDir0 = Some(od)
        jarListFile0 = defaultFile(Default.jarListFileName)
        apiNodesFile0 = defaultFile(Default.apiNodesFileName)
        decouple0 = defaultFile(Default.decoupleFileName)
        logFile0 = defaultFile(Default.logFileName)
    }
  }

  setWorkingDirectory(workingDirectory)

  /*private [this] var logger0 : PuckLogger = logFile match {
    case None => new PuckSystemLogger(logPolicy)
    case Some(f) => new PuckFileLogger (logPolicy, f)
  }*/
  private [this] var logger0 : PuckLogger = new PuckSystemLogger(logPolicy)


  def srcDirectory = this.srcDir0
  def srcDirectory_=(sdir : Option[File]) {
    this.srcDir0 = setCanonicalOptionFile(this.srcDir0, sdir)
  }

  def outDirectory = this.outDir0
  def outDirectory_=(sdir : Option[File]){
    this.outDir0 = setCanonicalOptionFile(this.outDir0, sdir)
  }

  def jarListFile = this.jarListFile0
  def jarListFile_=(sf : Option[File]){
    this.jarListFile0 = setCanonicalOptionFile(this.jarListFile0, sf)
  }

  def apiNodesFile = this.apiNodesFile0
  def apiNodesFile_=(sf : Option[File]){
    this.apiNodesFile0 = setCanonicalOptionFile(this.apiNodesFile0, sf)
  }

  def decouple = this.decouple0
  def decouple_=(sf: Option[File]){
    this.decouple0 = setCanonicalOptionFile(this.decouple0, sf)
  }


  private [this] var gdot : Option[File] = None

  def graphvizDot = this.gdot
  def graphvizDot_=(sf: Option[File]){
    this.gdot = setCanonicalOptionFile(this.gdot, sf)
  }

  private [this] var editor0 : Option[File] = None

  def editor = editor0
  def editor_=(sf: Option[File]){
    this.editor0 = setCanonicalOptionFile(this.editor0, sf)
  }

  def logFile = logFile0


  def graphFile(suffix : String) : File = outDirectory match {
    case None => throw new AGError("no output directory !!")
    case Some(d) => new File(d + File.separator + graphStubFileName + suffix)
  }



  def loadGraph(ll : AST.LoadingListener) : GraphT

  val dotHelper : DotHelper[Kind]

  def makeDot(graph : GraphT,
              printId : Boolean,
              printSignatures : Boolean,
              useOption : Option[AGEdge[Kind]],
              writer : OutputStreamWriter = new FileWriter(graphFile(".dot"))){
    DotPrinter.print(new BufferedWriter(writer), graph, dotHelper, printId,
      printSignatures, searchRoots = false, selectedUse = useOption)
  }

  /*def makeProlog(){
    PrologPrinter.print(new BufferedWriter(new FileWriter(graphFile(".pl"))), ag)
  }*/

  def convertDot( sInput : Option[InputStream] = None,
                  sOutput : Option[OutputStream] = None,
                  outputFormat : DotOutputFormat) : Int = {

    val dot = graphvizDot match {
      case None => "dot" // relies on dot directory being in the PATH variable
      case Some(f) => f.getCanonicalPath
    }

    val processBuilder =
      sInput match {
        case None => Process(List(dot,
          "-T" + outputFormat, graphFile(".dot").toString))
        case Some(input) => Process(List(dot,
          "-T" + outputFormat)) #< input
      }

    sOutput match {
      case None =>(processBuilder #> graphFile( "." + outputFormat)).!
      case Some(output) =>(processBuilder #> output).!
    }
  }

  def makePng(graph : GraphT,
              printId : Boolean = false,
              printSignatures : Boolean = false,
              sOutput : Option[OutputStream] = None,
              outputFormat : DotOutputFormat = Png(),
              selectedUse : Option[AGEdge[Kind]] = None)
             (finish : Try[Int] => Unit = {case _ => ()}){

    //TODO fix bug when chaining the two function with a pipe
    // and calling it in "do everything"
    makeDot(graph, printId, printSignatures, selectedUse)

    convertDot(sInput = None, sOutput, outputFormat)

    /*
        val pipedOutput = new PipedOutputStream()
        val pipedInput = new PipedInputStream(pipedOutput)

        println("launchig convert dot future")
        Future {
          convertDot(Some(pipedInput), sOutput, outputFormat)
        } onComplete finish
        println("post launching")


        makeDot(printId, printSignatures, selectedUse,
          writer = new OutputStreamWriter(pipedOutput))
        println("post make dot")
    */


  }



  def parseConstraints()  = {
    if(graphBuilder == null) throw new AGError("WTF !!")
    val parser = ConstraintsParser(graphBuilder)
    try {
      decouple match{
        case None => throw new AGError("cannot parse : no decouple file given")
        case Some(f) => parser(new FileReader(f))
      }
    } catch {
      case e : NoSuchElementException =>
        logger.writeln("parsing failed :" + e.getLocalizedMessage)(PuckLog.NoSpecialContext, PuckLog.Error)
    }
    graph = graph.newGraph(nConstraints = graphBuilder.constraintsMap)
  }

  /*def decisionMaker() : DecisionMaker[Kind]

  def solver(dm : DecisionMaker[Kind]) : Solver[Kind]

  def solve (trace : Boolean = false,
             decisionMaker : DecisionMaker[Kind]){

    var inc = 0

    def printTrace(){
      makePng(printSignatures = true,
        sOutput = Some(new FileOutputStream(graphFile( "_trace" + inc +".png"))))()
      inc += 1
    }

    graph.transformations.startRegister()
    solver(decisionMaker).solve()
    if(trace) {
      this.logger.writeln("*****************************************************")
      this.logger.writeln("*****************   merge done   ********************")
      this.logger.writeln("")
      printTrace()
    }
  }
  */

  def searchingStrategies : Seq[ConstraintSolvingSearchEngineBuilder[Kind, T]]


  type ST = SearchState[ResultT[Kind, T]]

  def explore (trace : Boolean = false,
               builder : ConstraintSolvingSearchEngineBuilder[Kind,T]) : Search[ResultT[Kind, T]] = {

    val engine = builder(graph)

    puck.util.Time.time(logger, defaultVerbosity) {
      engine.explore()
    }

    engine
  }


  def printCSSearchStatesGraph(states : Map[Int, Seq[SearchState[ResultT[Kind, T]]]]){
    val d = graphFile("_results")
    d.mkdir()
    states.foreach{
      case (cVal, l) =>
        val subDir = graphFile("_results%c%d".format(File.separatorChar, cVal))
        subDir.mkdir()
        printCSSearchStatesGraph(subDir, l, None)
    }
  }

  def printCSSearchStatesGraph(dir : File,
                               states : Seq[SearchState[ResultT[Kind,T]]],
                               sPrinter : Option[(SearchState[ResultT[Kind,T]] => String)]){

    val printer = sPrinter match {
      case Some(p) => p
      case None =>
        s : SearchState[ResultT[Kind,T]] => s.uuid()
    }

    states.foreach { s =>
      val (graph, recording) = s.result
      recording()
      val f = new File("%s%c%s.png".format(dir.getAbsolutePath, File.separatorChar, printer(s)))
      makePng(graph, printId = true, sOutput = Some(new FileOutputStream(f)))()
    }
  }

  //def printCode() : Unit


  //TODO ? change to List[String] ?
  val srcSuffix : String


  private def openList(files : Seq[String]){
    val ed = editor match {
      case None => sys.env("EDITOR")
      case Some(f) => f.getCanonicalPath
    }
    Process(ed  +: files ).!
  }

  import puck.util.FileHelper.findAllFiles

  def openSources() = openList(findAllFiles(srcDirectory.get, srcSuffix,
    outDirectory.get.getName))
  def openProduction() = openList(findAllFiles(outDirectory.get, srcSuffix,
    outDirectory.get.getName))

}