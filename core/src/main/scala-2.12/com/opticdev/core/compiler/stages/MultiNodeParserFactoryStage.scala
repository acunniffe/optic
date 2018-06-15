package com.opticdev.core.compiler.stages
import com.opticdev.marvin.common.helpers.InRangeImplicits._
import com.opticdev.common.PackageRef
import com.opticdev.core.compiler.errors.{AstPathNotFound, ErrorAccumulator}
import com.opticdev.core.compiler.helpers.FinderPath
import com.opticdev.core.compiler.{FinderStageOutput, MultiNodeLensOutput, ParserFactoryOutput, SnippetStageOutput}
import com.opticdev.core.sourcegear.{CompiledLens, CompiledMultiNodeLens}
import com.opticdev.core.sourcegear.accumulate.MapSchemaListener
import com.opticdev.core.sourcegear.containers.{ContainerMapping, SubContainerManager}
import com.opticdev.core.sourcegear.context.SDKObjectsResolvedImplicits
import com.opticdev.core.sourcegear.gears.parsing.{AdditionalParserInformation, ParseAsModel}
import com.opticdev.core.sourcegear.variables.VariableManager
import com.opticdev.parsers.AstGraph
import com.opticdev.parsers.graph.CommonAstNode
import com.opticdev.parsers.graph.path.FlatWalkablePath
import com.opticdev.sdk.descriptions.{Component, Lens, RuleWithFinder, SchemaRef}
import play.api.libs.json.JsObject

import scala.util.Try

class MultiNodeParserFactoryStage(snippetStage: SnippetStageOutput, qualifySchema: (PackageRef, SchemaRef) => SchemaRef = (a,b) => b)(implicit val lens: Lens) extends CompilerStage[MultiNodeLensOutput] {
  implicit val snippetStageOutput = snippetStage

  override def run: MultiNodeLensOutput = {
    MultiNodeLensOutput(
      CompiledMultiNodeLens(lens.name, lens.id, lens.packageRef, lens.schema, snippetStageOutput.enterOn, childLenses.getOrElse(throw childLenses.failed.get))
    )
  }


  def childLenses: Try[Vector[CompiledLens]] = {

    val errorAccumulator = new ErrorAccumulator

    val variableManager = new VariableManager(lens.variables, snippetStageOutput.parser.identifierNodeDesc)

    for {
      childSnippets <- Try(toChildrenSnippetOutputs)
      containersIndexed <- Try(containersByChild(childSnippets))
      (componentsIndexed, rulesIndexed) <- Try(componentsAndRulesByChild(childSnippets))
      compiledLenses <- Try {
        childSnippets.zipWithIndex.map {
          case (snippet, index) => {

            val containers = containersIndexed(index)
            val components = componentsIndexed(index)
            val rules = rulesIndexed(index)

            implicit val childLens = Lens(
                                        None,
                                        id(index),
                                        schemaId(index),
                                        snippet.snippet,
                                        components.values.flatten.toVector,
                                        lens.variables,
                                        lens.subcontainers.filter(i=> containers.exists(_._1.name == i.name)),
                                        lens.packageRef, None)

            implicit val subcontainersManager = new SubContainerManager(childLens.subcontainers, snippet.containerMapping)

            val finderStage = new FinderStage(snippet)(childLens, errorAccumulator, variableManager, subcontainersManager).run

            val parser = new ParserFactoryStage(snippet, finderStage, qualifySchema)(childLens, variableManager, subcontainersManager).run
            val renderer = new RenderFactoryStage(snippet, parser.parseGear)(childLens).run

            CompiledLens(childLens.name, childLens.id, childLens.packageRef, childLens.schema, snippet.enterOn, parser.parseGear.asInstanceOf[ParseAsModel], renderer.renderGear,
              internal = true)
          }
        }
      }
    } yield compiledLenses

  }

  //id
  def id(index: Int) = lens.id+"____"+index
  def schemaId(index: Int) = lens.schema.copy(id = lens.schema.id+"____"+index)

  //steps

  def toChildrenSnippetOutputs: Vector[SnippetStageOutput] = {
    snippetStageOutput.entryChildren.map(child=> {
      val childRaw = snippetStageOutput.snippet.block.substring(child.range)
      new SnippetStage(snippetStageOutput.snippet.copy(block = childRaw)).run
    })
  }

  def containersByChild(childSnippets: Vector[SnippetStageOutput] = toChildrenSnippetOutputs): Vector[ContainerMapping] =
    childSnippets.map(_.containerMapping)

  def componentsAndRulesByChild(childSnippets: Vector[SnippetStageOutput] = toChildrenSnippetOutputs): (Vector[Map[FinderPath, Vector[Component]]], Vector[Map[FinderPath, Vector[RuleWithFinder]]]) = {
    val result = new FinderStage(snippetStage).run
    import com.opticdev.core.sourcegear.graph.GraphImplicits._
    val children = snippetStage.rootNode.children(snippetStage.astGraph).map(_._2)

    val components = children.map(child =>
      result.componentFinders.collect{ case t if child.hasChild(t._1.targetNode)(snippetStage.astGraph) => t })

    val rules = children.map(child =>
      result.ruleFinders.collect{ case t if child.hasChild(t._1.targetNode)(snippetStage.astGraph) => t })


    (components, rules)
  }

}


