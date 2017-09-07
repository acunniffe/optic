package Fixture.compilerUtils

import compiler.SnippetStageOutput
import compiler.stages.{FinderStage, ParserFactoryStage, SnippetStage}
import sdk.descriptions.{Component, Lens, Rule, Snippet}
import sourcegear.gears.parsing.{ParseAsModel, ParseGear}

trait ParserUtils {

  def parseGearFromSnippetWithComponents(block: String, components: Vector[Component], rules: Vector[Rule] = Vector()) : ParseAsModel = {
    val snippet = Snippet("Testing", "Javascript", "es6", block)
    implicit val lens : Lens = Lens("Example", null, snippet, rules, components)

    val snippetBuilder = new SnippetStage(snippet)
    val snippetOutput = snippetBuilder.run
    val finderStage = new FinderStage(snippetOutput)
    val finderStageOutput = finderStage.run
    val parserFactoryStage = new ParserFactoryStage(snippetOutput, finderStageOutput)
    val output = parserFactoryStage.run

    output.parseGear.asInstanceOf[ParseAsModel]
  }

  def sample(block: String) : SnippetStageOutput = {
    val snippet = Snippet("Testing", "Javascript", "es6", block)
    implicit val lens : Lens = Lens("Example", null, snippet, Vector(), Vector())
    val snippetBuilder = new SnippetStage(snippet)
    snippetBuilder.run
  }


}