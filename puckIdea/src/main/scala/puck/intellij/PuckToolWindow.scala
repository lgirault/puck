package puck.intellij

import java.io.File
import javax.swing.JPanel

import com.intellij.openapi.module.{ModuleUtilCore, Module}
import com.intellij.openapi.roots.{ProjectRootManager, ModuleRootManager}
import com.intellij.openapi.vfs.VirtualFile

import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.search.FilenameIndex
import com.intellij.ui.content.ContentManager
import puck.graph.ConcreteNode
import puck.graph.io._
import puck.gui.svg.{SVGPanel, SVGController}
import puck.intellij.graphBuilding.IntellijGraphBuilder
import puck.javaGraph._

import scalaz.syntax.std.option._

// import com.intellij.openapi.roots.{ProjectRootManager, JavaProjectRootsUtil}
// import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
//      val mconf = new ModulesConfigurator(project)
//      val modules = mconf.getModules

//      val prootManager = ProjectRootManager.getInstance(project)
//      prootManager.getContentSourceRoots

//    modules(0).getModuleScope
//    JavaProjectRootsUtil.isOutsideJavaSourceRoot()







object PuckToolWindow {

  val id = "PuckToolWindow"

  var generatedSourcesDirName = "src"

  var sToolWindow : Option[ToolWindow] = None

  var sSvgController : Option[SVGController] = None

  var sCurrentModule : Option[Module] = None

  def moduleDir : Option[VirtualFile] =
    sCurrentModule map (_.getModuleFile.getParent)
//    sCurrentModule map ModuleUtilCore.getModuleDirPath

  def generatedSourcesRoot : Option[VirtualFile] =
    moduleDir flatMap  (_.getChildren.find(_.getName == generatedSourcesDirName))

  def generatedSourcesRootVirtualFile : Option[VirtualFile] = sCurrentModule flatMap {
    module =>
      val roots = ModuleRootManager.getInstance(module).getSourceRoots()
      roots.find(_.getName == generatedSourcesDirName)
  }

  def packageDirectoryInGeneratedSource(path : Seq[ConcreteNode]) : Option[VirtualFile] =
    PuckToolWindow.generatedSourcesRoot flatMap {
      root =>
        path.foldLeft(root.some) {
          (sParent, n) =>
            sParent flatMap ( p => Option(p.findChild(n.name)) )
        }
    }

  def createPanel(m : Module)  = {
    sCurrentModule = Some(m)
    val builder = new IntellijGraphBuilder(m)
    val dg2ast = builder.build()
    val g = dg2ast.initialGraph

    val printingOptions =
      new PrintingOptions(defaultVisibilitySet(dg2ast),
        printId = true, printSignatures = true,
        redOnly = false, printVirtualEdges = false)

    val controlBuilder =
      IntellijSVGController.builderFromModule(m, printingOptions, JGraphUtils, dg2ast)


    val p = new SVGPanel(controlBuilder) with Runnable {
      def run() : Unit = this.revalidate()
    }
    sSvgController = Some(p.controller)
    //ProjectRootManager.getInstance(m.getProject)
    //ModuleRootManager.getInstance(m)

    moduleDir foreach {
      dir =>
        val decouple = new File(dir + File.separator +"decouple.pl")

        if(decouple.exists())
          parseConstraint(decouple)
    }

    p
  }

  def parseConstraint(file : File) : Unit =
    sSvgController foreach (_.parseConstraints(file))

  def setModule( m : Module) : Unit = sToolWindow foreach {
    toolWindow =>
    val panel = createPanel(m)

    val contentManager: ContentManager = toolWindow.getContentManager
    val content = contentManager.getFactory.createContent(panel, null, false)
    contentManager.removeAllContents(true)
    contentManager.addContent(content)

    toolWindow.activate(panel)
  }


  def defaultVisibilitySet(dg2ast: DG2AST) : VisibilitySet.T = {
    val g = dg2ast.initialGraph
    val s = VisibilitySet.allVisible(g)
    import VisibilitySet.VisibilitySetOps

    def f(s : VisibilitySet.T, rootName : String, includeRoot : Boolean) =
      dg2ast.nodesByName.get(rootName) map (r =>
        s.setVisibility(g.subTree(r, includeRoot), Hidden)) getOrElse s


    val s2 = f(s, "@primitive", includeRoot = true)
    f(s2, "java", includeRoot = false)
  }

  def initWithEmptyPanel() : Unit = sToolWindow foreach {
    toolWindow =>
      val contentManager: ContentManager = toolWindow.getContentManager
      val content = contentManager.getFactory.createContent(new JPanel, null, false)
      contentManager.addContent(content)
  }

}
