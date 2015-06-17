package puck.gui.svg

import java.io.{File, InputStream, PipedInputStream, PipedOutputStream}
import javax.swing.JMenuItem

import org.apache.batik.dom.svg.SAXSVGDocumentFactory
import org.apache.batik.swing.JSVGCanvas
import org.apache.batik.util.XMLResourceDescriptor
import org.w3c.dom.Element
import org.w3c.dom.svg.{SVGGElement, SVGDocument}
import puck.graph._
import puck.graph.io._
import puck.graph.transformations.MileStone
import puck.gui.TextAreaLogger
import puck.gui.svg.actions.{AddNodeAction, AbstractionAction}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

import  VisibilitySet._
trait StackListener{
  def update(svgController: SVGController) : Unit
}


class SVGController private
( val filesHandler: FilesHandler,
  val graphUtils : GraphUtils,
  val dg2ast: DG2AST,
  val svgCanvas : JSVGCanvas,
  val console : SVGConsole,
  private var visibility : VisibilitySet.T,
  private var printId : Boolean,
  private var printSignatures : Boolean,
  private var printVirtualEdges : Boolean = false,
  private var printConcreteUsesPerVirtualEdges : Boolean = true,
  private var printRedOnly : Boolean = false,
  private var selectedEdgeForTypePrinting : Option[DGUses] = None) {

  implicit val consoleLogger = new TextAreaLogger(console.textArea, _ => true )

  def nodesByName : Map[String, NodeId] =
      dg2ast.nodesByName


  private val undoStack = mutable.Stack[DependencyGraph]()
  private val redoStack = mutable.Stack[DependencyGraph]()

  private val stackListeners = mutable.ArrayBuffer[StackListener]()

  def registerAsStackListeners(l : StackListener) =
    stackListeners.append(l)

  def updateStackListeners() : Unit =
    stackListeners.foreach(_.update(this))


  type Color = String

  def setSignatureVisible(b : Boolean): Unit = {
    if( b != printSignatures ){
      printSignatures = b
      displayGraph(graph)
    }
  }
  def setSelectedEdgeForTypePrinting(se: Option[DGUses]) : Unit = {
    if( se != selectedEdgeForTypePrinting ){
      selectedEdgeForTypePrinting = se
      displayGraph(graph)
    }
  }

  def setIdVisible(b : Boolean): Unit = {
    if( b != printId ){
      printId = b
      displayGraph(graph)
    }
  }

  def setVirtualEdgesVisible(b : Boolean): Unit = {
    if( b != printVirtualEdges ){
      printVirtualEdges = b
      displayGraph(graph)
    }
  }
  def setConcreteUsesPerVirtualEdges(b : Boolean): Unit = {
    if( b != printConcreteUsesPerVirtualEdges ){
      printConcreteUsesPerVirtualEdges = b
      displayGraph(graph)
    }
  }

  def setRedEdgesOnly(b : Boolean): Unit = {
    if( b != printRedOnly ){
      printRedOnly = b
      displayGraph(graph)
    }
  }

  var selectedNodes0: List[(NodeId, Color, Element)] = List()
  def selectedNodes: List[(NodeId, Color, Element)] = selectedNodes0

  def getSelectedNode(nodeId: NodeId) : Option[(NodeId, Color, Element)]=
    selectedNodes.find( _._1 == nodeId)

  def isSelected(nodeId: NodeId) : Boolean =
    selectedNodes.exists( _._1 == nodeId)

  def removeSelectedNode(nodeId: NodeId) : Unit =
    selectedNodes0 = selectedNodes0.filter(_._1 != nodeId)

  def keepOnlySelectedNode(nodeId: NodeId) : List[(NodeId, Color, Element)] = {
    val (keep, others) = selectedNodes partition (_._1 == nodeId)
    selectedNodes0 = keep
    others
  }


  def hide(id : NodeId): Unit = {
    visibility = visibility.setVisibility(id, Hidden)
    setSubTreeVisibility(id, Hidden)
  }

  private def setSubTreeVisibility(rootId : NodeId, v : Visibility): Unit ={
    val nodes = graph.subTree(rootId, includeRoot = false)
    visibility = visibility.setVisibility(nodes, v)
    displayGraph(graph)
  }

  def collapse(root: NodeId) : Unit =
    setSubTreeVisibility(root, Hidden)

  def expand(root: NodeId) : Unit = {
    visibility =
      graph.content(root).foldLeft(visibility)(_.setVisibility(_, Visible))

    displayGraph(graph)
  }

  def expandAll(root: NodeId) : Unit =
    setSubTreeVisibility(root, Visible)

  val defaultColor = "black"

  def addNodeToSelection(id: NodeId, elt: Element): Unit = {
    println("setting selectedNode")
    val color =
      if(elt.getAttribute("fill").nonEmpty)
        elt.getAttribute("fill")
      else defaultColor
    selectedNodes0 :+= ((id, color, elt))
    console.displaySelection(graph.getNode(id)+"")
  }

  def resetSelectedNodes(): Unit = {
    selectedNodes0 = List()
    console.displaySelection("")
  }

  var selectedEdge0 : Option[(DGEdge, Color, SVGGElement)] = None
  def selectedEdge : Option[(DGEdge, Color, SVGGElement)] = selectedEdge0

  def setEdgeSelected(dgEdge: DGEdge, elt : SVGGElement, c : Color) = {
    selectedEdge0 = Some((dgEdge, c, elt))
    println("setting selectedEdge")
    import ShowDG._
    console.displaySelection(showDG[DGEdge](graph).shows(dgEdge))
  }

  def resetEdgeSelected(): Unit = {
    selectedEdge0 = None
    console.displaySelection("")
  }

  def showCode(nodeId: NodeId): Unit = {
    console.appendText("Code : ")
    console.appendText(dg2ast.code(graph, nodeId))
  }

  def printingOptions =
    PrintingOptions(visibility, printId, printSignatures,
    selectedEdgeForTypePrinting,
    printVirtualEdges, printConcreteUsesPerVirtualEdges,
    printRedOnly)

  def displayGraph(graph: DependencyGraph) : Unit = {
    SVGController.documentFromGraph(graph, filesHandler, printingOptions)(console.appendText)
    {case doc => svgCanvas.setDocument(doc)}
  }



  def canUndo = undoStack.nonEmpty

  def undoAll() = {

    while(undoStack.nonEmpty)
      redoStack.push(undoStack.pop())

    displayGraph(graph)
    updateStackListeners()
  }

  def undo() = {
      redoStack.push(undoStack.pop())
      displayGraph(graph)
      updateStackListeners()
  }
  def canRedo = redoStack.nonEmpty
  def redo()={
    undoStack.push(redoStack.pop())
    displayGraph(graph)
    updateStackListeners()
  }
  def pushGraph(graph: DependencyGraph) = {
    undoStack.push(graph)
    redoStack.clear()
    displayGraph(graph)
    updateStackListeners()
  }

  def graph =
    if(undoStack.nonEmpty) undoStack.head
    else dg2ast.initialGraph

  def saveRecordOnFile(file : File) : Unit = {
    Recording.write(file.getAbsolutePath, nodesByName, graph)
  }

  def loadRecord(file : File) : Unit = {
    val r = Recording.load(file.getAbsolutePath, nodesByName)

    pushGraph(r.reverse.foldLeft(graph){
      case (g, MileStone) =>
        undoStack.push(g)
        MileStone.redo(g)
      case (g, t) => t.redo(g)
    })
  }


  def applyOnCode() : Unit = {
    dg2ast(graph)

    filesHandler.outDirectory match {
      case None => console.appendText("no output directory : cannot print code")
      case Some(d) => dg2ast.printCode(d)
    }

  }

  def abstractionChoices(n: ConcreteNode): Seq[JMenuItem] =
    n.kind.abstractionChoices.map { case (k, p) =>
      new JMenuItem(new AbstractionAction(n, p, k, this))
    }

  def abstractionChoices(id: NodeId): Seq[JMenuItem] =
    graph.getNode(id) match {
      case n: ConcreteNode => abstractionChoices(n)
      case vn: VirtualNode => Seq.empty
    }

  def childChoices(n : ConcreteNode) : Seq[JMenuItem] = {
    val ks = graph.nodeKinds.filter(n.kind.canContain)
    ks map {k => new JMenuItem(new AddNodeAction(n, this, k))}
  }
}


object SVGController {

  def apply(filesHandler: FilesHandler,
            graphUtils : GraphUtils,
            dg2ast : DG2AST,
            opts : PrintingOptions,
            svgCanvas : JSVGCanvas,
            console : SVGConsole): SVGController ={
    val c = new SVGController(filesHandler, graphUtils, dg2ast, svgCanvas, console,
                opts.visibility, opts.printId, opts.printSignatures)
    c.pushGraph(dg2ast.initialGraph)
    c.displayGraph(dg2ast.initialGraph)
    c
  }

  def documentFromStream(stream: InputStream): SVGDocument = {
    val parser: String = XMLResourceDescriptor.getXMLParserClassName
    val factory: SAXSVGDocumentFactory = new SAXSVGDocumentFactory(parser)
    factory.createSVGDocument("", stream)
  }

  def documentFromGraph
  (graph: DependencyGraph,
   filesHandler: FilesHandler,
   printingOptions: PrintingOptions)
  (onDotConversionResult: String => Unit)
  (onDocBuildingSuccess : PartialFunction[SVGDocument, Unit]) = {

    val pipedOutput = new PipedOutputStream()
    val pipedInput = new PipedInputStream(pipedOutput)
    val fdoc = Future {
      SVGController.documentFromStream(pipedInput)
    }

    filesHandler.makeImage(graph, printingOptions, Some(pipedOutput), Svg){
      res =>
      val msg = res match {
       case Success(0) => ""
        case Success(n) =>
          "An error occured during the production of the SVG file, " +
            "produced dot file can be found in out/graph.dot"
        case Failure(errMsg) =>
          "Image creation failure : " + errMsg

      }
      onDotConversionResult(msg)
    }

    fdoc.onSuccess(onDocBuildingSuccess)
  }
}