package com.useoptic.diff.initial

import com.useoptic.contexts.rfc.Commands.RfcCommand
import com.useoptic.contexts.rfc.{RfcCommandContext, RfcService, RfcServiceJSFacade}
import com.useoptic.contexts.shapes.Commands.ShapeId
import com.useoptic.contexts.shapes.ShapesHelper.StringKind
import com.useoptic.diff.shapes.{JsonLikeAndSpecDiffVisitors, JsonLikeAndSpecTraverser, ShapeDiffResult}
import com.useoptic.diff.shapes.resolvers.DefaultShapesResolvers
import com.useoptic.dsa.OpticIds
import com.useoptic.end_to_end.fixtures.JsonExamples
import com.useoptic.types.capture.{JsonLike, JsonLikeFrom}
import org.scalatest.FunSpec

import scala.util.Try

class DistributionAwareShapeBuilderSpec extends FunSpec {
  implicit val shapeBuildingStrategy = ShapeBuildingStrategy.inferPolymorphism
  describe("aggregate values by trails") {

    lazy val todoMap = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
      JsonLikeFrom.json(JsonExamples.basicTodo).get,
      JsonLikeFrom.json(JsonExamples.basicTodoWithDescription).get,
      JsonLikeFrom.json(JsonExamples.basicTodoWithoutStatus).get,
    ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)


    it("can stage object for creation with some optional fields") {
      val shapesToMake = DistributionAwareShapeBuilder.toShapes(todoMap)
      assert(shapesToMake.isInstanceOf[ObjectWithFields])
      assert(shapesToMake.asInstanceOf[ObjectWithFields].fields.size == 3)
      println(shapesToMake.asInstanceOf[ObjectWithFields].fields)
      assert(shapesToMake.asInstanceOf[ObjectWithFields].fields.count(_.shape.isInstanceOf[OptionalShape]) == 2)
    }

    it("can stage array for creation with example items all of same type") {

      lazy val stringArrayMap = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
        JsonLikeFrom.json(JsonExamples.stringArray).get
      ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

      val shapesToMake = DistributionAwareShapeBuilder.toShapes(stringArrayMap)
      assert(shapesToMake.isInstanceOf[ListOfShape])
      assert(shapesToMake.asInstanceOf[ListOfShape].shape.isInstanceOf[PrimitiveKind])
      assert(shapesToMake.asInstanceOf[ListOfShape].shape.asInstanceOf[PrimitiveKind].baseShape == StringKind)
    }

    it("can stage array for creation with example items all of different types") {

      lazy val stringArrayMap = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
        JsonLikeFrom.json(JsonExamples.stringArrayWithNumbers).get
      ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

      val shapesToMake = DistributionAwareShapeBuilder.toShapes(stringArrayMap)
      assert(shapesToMake.isInstanceOf[ListOfShape])
      assert(shapesToMake.asInstanceOf[ListOfShape].shape.isInstanceOf[OneOfShape])
      assert(shapesToMake.asInstanceOf[ListOfShape].shape.asInstanceOf[OneOfShape].branches.size == 2)
    }

    it("can stage array with unknown type") {

      lazy val stringArrayMap = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
        JsonLikeFrom.json(JsonExamples.emptyArray).get
      ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

      val shapesToMake = DistributionAwareShapeBuilder.toShapes(stringArrayMap)
      assert(shapesToMake.isInstanceOf[ListOfShape])
      assert(shapesToMake.asInstanceOf[ListOfShape].shape.isInstanceOf[Unknown])
    }

    describe("nullable") {

      it("can stage a nullable of unknown if all examples were null") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.objectWithNull).get,
          JsonLikeFrom.json(JsonExamples.objectWithNull).get,
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)

        val field = shapesToMake.asInstanceOf[ObjectWithFields].fields.head
        val fieldShapeAsNullable = field.shape.asInstanceOf[NullableShape]
        assert(fieldShapeAsNullable.shape.isInstanceOf[Unknown])
      }

      it("root one of") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.emptyArray).get,
          JsonLikeFrom.json(JsonExamples.emptyObject).get,
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)
        assert(shapesToMake.isInstanceOf[OneOfShape])
      }

      it("can stage a nullable of string") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.objectWithNull).get,
          JsonLikeFrom.json(JsonExamples.objectWithNullAsString).get,
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)

        val field = shapesToMake.asInstanceOf[ObjectWithFields].fields.head
        val fieldShapeAsNullable = field.shape.asInstanceOf[NullableShape]
        assert(fieldShapeAsNullable.shape.isInstanceOf[PrimitiveKind])
      }

      it("can stage a nullable of one of string number") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.objectWithNull).get,
          JsonLikeFrom.json(JsonExamples.objectWithNullAsString).get,
          JsonLikeFrom.json(JsonExamples.objectWithNullAsNumber).get,
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)

        val field = shapesToMake.asInstanceOf[ObjectWithFields].fields.head
        val fieldShapeAsNullable = field.shape.asInstanceOf[NullableShape]
        assert(fieldShapeAsNullable.shape.isInstanceOf[OneOfShape])
      }

    }

    describe("lists of objects") {
      it("can stage a list of the same object shape") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.objectsWithOptionalsArray).get
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)
        val fields = shapesToMake.asInstanceOf[ListOfShape].shape.asInstanceOf[ObjectWithFields].fields

        assert(fields.forall(i => i.shape.isInstanceOf[OptionalShape]))
      }

      it("can stage a list of objects and strings") {
        lazy val map = DistributionAwareShapeBuilder.aggregateTrailsAndValues(Vector(
          JsonLikeFrom.json(JsonExamples.objectsAndStringsInArray).get
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        val shapesToMake = DistributionAwareShapeBuilder.toShapes(map)
        val branches = shapesToMake.asInstanceOf[ListOfShape].shape.asInstanceOf[OneOfShape].branches
        assert(branches(0).isInstanceOf[PrimitiveKind])
        assert(branches(1).isInstanceOf[ObjectWithFields])
      }
    }

    describe("to commands") {

      def tryCommands(commands: Vector[RfcCommand], shapeId: ShapeId, example: JsonLike) = {
        val commandContext: RfcCommandContext = RfcCommandContext("a", "b", "c")
        val eventStore = RfcServiceJSFacade.makeEventStore()
        implicit val ids = OpticIds.newDeterministicIdGenerator
        val rfcService: RfcService = new RfcService(eventStore)
        rfcService.handleCommandSequence("id", commands, commandContext)
        val rfcState = rfcService.currentState("id")
        val resolvers = new DefaultShapesResolvers(rfcState)

        val diffs = scala.collection.mutable.ListBuffer[ShapeDiffResult]()
        val traverser = new JsonLikeAndSpecTraverser(resolvers, rfcState, new JsonLikeAndSpecDiffVisitors(resolvers, rfcState, e => diffs.append(e), _ => Unit))
        traverser.traverseRootShape(Some(example), shapeId)
        assert(diffs.isEmpty)
        (rfcState, diffs)
      }

      it("can create commands for basic shape") {

        val commands = DistributionAwareShapeBuilder.toCommands(Vector(
          JsonLikeFrom.json(JsonExamples.basicTodo).get,
          JsonLikeFrom.json(JsonExamples.basicTodoWithDescription).get
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        tryCommands(commands._2.flatten, commands._1, JsonLikeFrom.json(JsonExamples.basicTodo).get)
      }

      it("can create commands for array of strings") {
        lazy val commands = DistributionAwareShapeBuilder.toCommands(Vector(
          JsonLikeFrom.json(JsonExamples.stringArray).get
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        tryCommands(commands._2.flatten, commands._1, JsonLikeFrom.json(JsonExamples.stringArray).get)
      }

      it("can create a one of") {

        lazy val commands = DistributionAwareShapeBuilder.toCommands(Vector(
          JsonLikeFrom.json(JsonExamples.stringArray).get,
          JsonLikeFrom.json(JsonExamples.basicTodo).get
        ))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

        tryCommands(commands._2.flatten, commands._1, JsonLikeFrom.json(JsonExamples.stringArray).get)
      }

    }

  }

}
