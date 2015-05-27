package puck.gui.svg

import java.awt.event.ActionEvent
import java.awt.{Dimension, BorderLayout}
import java.io.{File, InputStream}
import javax.swing._

import puck.graph.GraphUtils
import puck.graph.io.{DG2AST, FilesHandler, PrintingOptions}
import puck.gui.svg.actions.AddNodeAction
import puck.gui.{PuckConsolePanel, TextAreaLogger}
import puck.javaGraph.nodeKind.Package

import scala.swing.Label

class SVGConsole
( val lines: Int = 10,
  val charPerLine: Int = 50) {

  private val selection: Label = new Label

  val panel0 = new PuckConsolePanel(){
    selection +=: contents
    console.rows = lines
    console.columns = charPerLine
  }

  def panel = panel0.peer
  def console = panel0.console

  private[svg] def displaySelection(node: String) : Unit = {
    if (node.length > 0) selection.text = "Selection : " + node
    else selection.text = "No selection"
  }

  def appendText(txt: String) : Unit = {
    console.append(txt + "\n")
  }
}

class SVGFrame
( stream: InputStream,
  opts: PrintingOptions,
  filesHandler : FilesHandler,
  graphUtils : GraphUtils,
  dg2ast : DG2AST
  ) extends JFrame with StackListener {

  def update(svgController: SVGController) : Unit = {
    undoAllButton.setEnabled(svgController.canUndo)
    undoButton.setEnabled(svgController.canUndo)
    redoButton.setEnabled(svgController.canRedo)
  }

  def abstractAction(name:String)
                    (action : ActionEvent => Unit) : AbstractAction =
    new AbstractAction(name){
      def actionPerformed(e: ActionEvent) : Unit = action(e)

    }
  def jbutton(name:String)
              (action : ActionEvent => Unit) : JButton =
   new JButton(abstractAction(name)(action))


  def addButtonToMenu(action : AbstractAction) : Unit = {
    val _ = menu.add(new JButton(action))
  }

  def addButtonToMenu(name:String)
                     (action : ActionEvent => Unit) : Unit =
    addButtonToMenu(abstractAction(name)(action))



  private val consolePanel: SVGConsole = new SVGConsole()
  setLayout(new BorderLayout)
  val panel : SVGPanel =
    new SVGPanel(SVGController.documentFromStream(stream))

  setVisible(true)

  val consoleLogger = new TextAreaLogger(consolePanel.console, dg2ast.initialGraph.logger.askPrint)

  private val controller: SVGController =
    SVGController(filesHandler,
                  graphUtils,
                  dg2ast,
      opts, panel.canvas, consolePanel)
  panel.controller = controller
  private val menu: JPanel = new JPanel()
  controller.registerAsStackListeners(this)

  addVisibilityCheckBoxesToMenu()


  private val undoAllButton = new JButton(abstractAction("Undo all") {
    _ => controller.undoAll()
  })
  undoAllButton.setEnabled(false)
  menu.add(undoAllButton)

  private val undoButton = new JButton(abstractAction("Undo") {
    _ => controller.undo()
  })
  undoButton.setEnabled(false)
  menu.add(undoButton)
  private val redoButton = new JButton(abstractAction("Redo"){
    _ => controller.redo()
  })
  redoButton.setEnabled(false)
  menu.add(redoButton)

  addLoadSaveButton()

  menu.add(consolePanel.panel)

  private def addHboxButtons() : Unit = {
    val hbox = new JPanel()
    hbox.setLayout(new BoxLayout(hbox, BoxLayout.Y_AXIS))
    menu add hbox

    hbox add jbutton("Apply") {
      _ => controller.applyOnCode()
    }

    hbox add new JButton(
      new AddNodeAction(controller.graph.root, controller, Package))


    hbox add jbutton("Show top level packages") {
        _ => controller.expand(controller.graph.rootId)
    }

    val _ = hbox add jbutton("Hide type relationship") {
      _ => controller.setSelectedEdgeForTypePrinting(None)
    }
  }

  addHboxButtons()

  this.add(panel, BorderLayout.CENTER)
  this.add(menu, BorderLayout.SOUTH)


  this.setVisible(true)
  this.setMinimumSize(new Dimension(640, 480))
  this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)



  private def chooseFile(chooserMode : (JFileChooser, java.awt.Component) => Int) : Option[File] = {
    val chooser = new JFileChooser()
    chooser.setCurrentDirectory(filesHandler.workingDirectory)
    val returnVal: Int = chooserMode(chooser, SVGFrame.this)
    if (returnVal == JFileChooser.APPROVE_OPTION) {
      Some(chooser.getSelectedFile)
    }
    else None
  }

  private def openFile() : Option[File] =
    chooseFile( (chooser, component) => chooser.showOpenDialog(component) )
  private def saveFile() : Option[File] =
    chooseFile( (chooser, component) => chooser.showSaveDialog(component) )



  private def addLoadSaveButton() : Unit = {
    addButtonToMenu("Save") {
      _ =>
        saveFile() match {
          case None => consolePanel.appendText("no file selected")
          case Some(f) =>  controller.saveRecordOnFile(f)
        }
      }
    addButtonToMenu("Load") {
      _ =>
        openFile() match {
          case None => consolePanel.appendText("no file selected")
          case Some(f) => controller.loadRecord(f)
        }
    }
  }

  private def addVisibilityCheckBoxesToMenu() : Unit = {
    val hbox = new JPanel()
    hbox.setLayout(new BoxLayout(hbox, BoxLayout.Y_AXIS))
    menu add hbox

    def addCheckBox(name: String)(f: Boolean => Unit) : JCheckBox = {
      val checkBox: JCheckBox = new JCheckBox
      checkBox.setAction(new AbstractAction(name) {
        def actionPerformed(e: ActionEvent) : Unit = f(checkBox.isSelected)

      })
      hbox add checkBox
      checkBox
    }

    addCheckBox("Show signatures") {
        controller.setSignatureVisible
    }

    addCheckBox("Show ids") {
        controller.setIdVisible
    }
    addCheckBox("Show Virtual Edges") {
      controller.setVirtualEdgesVisible
    }
    val b = addCheckBox("Conrete Uses/Virtual Edge") {
      controller.setConcreteUsesPerVirtualEdges
    }
    b.setSelected(true)
    addCheckBox("Show RedOnly") {
      controller.setRedEdgesOnly
    }
    ()
  }


}
