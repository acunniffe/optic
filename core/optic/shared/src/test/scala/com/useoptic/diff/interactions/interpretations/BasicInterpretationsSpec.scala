package com.useoptic.diff.interactions.interpretations

import com.useoptic.contexts.requests.Commands
import com.useoptic.contexts.requests.Commands.ShapedBodyDescriptor
import com.useoptic.contexts.rfc.RfcState
import com.useoptic.diff.helpers.DiffHelpers
import com.useoptic.diff.initial.{DistributionAwareShapeBuilder, ShapeBuildingStrategy}
import com.useoptic.diff.interactions.interpreters.BasicInterpreters
import com.useoptic.diff.interactions.{InteractionTrail, Method, RequestBody, ResponseBody, SpecPath, SpecRequestBody, SpecRequestRoot, SpecResponseBody, SpecResponseRoot, TestHelpers, Traverser, UnmatchedRequestBodyContentType, UnmatchedRequestBodyShape, UnmatchedResponseBodyContentType, UnmatchedResponseBodyShape, Url}
import com.useoptic.diff.shapes.{JsonTrail, ListItemTrail, ObjectFieldTrail, ShapeTrail, UnmatchedShape}
import com.useoptic.diff.shapes.JsonTrailPathComponent._
import com.useoptic.diff.shapes.resolvers.DefaultShapesResolvers
import com.useoptic.dsa.OpticIds
import com.useoptic.types.capture._
import io.circe.Json
import org.scalatest.FunSpec
import io.circe.literal._

object InteractionHelpers {
  def simplePut(requestBody: Json, statusCode: Int = 200, contentType: String = "application/json"): HttpInteraction = {
    HttpInteraction(
      "uuid",
      Request(
        "some.host",
        "PUT",
        "/",
        ArbitraryData(None, None, None),
        ArbitraryData(None, None, None),
        Body(Some(contentType), ArbitraryData(None, Some(requestBody.noSpaces), None))
      ),
      Response(
        statusCode,
        ArbitraryData(None, None, None),
        Body(None, ArbitraryData(None, None, None))
      ),
      Vector()
    )
  }

  def simplePost(requestBody: Json, statusCode: Int = 200, contentType: String = "application/json"): HttpInteraction = {
    HttpInteraction(
      "uuid",
      Request(
        "some.host",
        "POST",
        "/",
        ArbitraryData(None, None, None),
        ArbitraryData(None, None, None),
        Body(Some(contentType), ArbitraryData(None, Some(requestBody.noSpaces), None))
      ),
      Response(
        statusCode,
        ArbitraryData(None, None, None),
        Body(None, ArbitraryData(None, None, None))
      ),
      Vector()
    )
  }

  def simpleGet(responseBody: Json, statusCode: Int = 200, contentType: String = "application/json"): HttpInteraction = {
    HttpInteraction(
      "uuid",
      Request(
        "some.host",
        "GET",
        "/",
        ArbitraryData(None, None, None),
        ArbitraryData(None, None, None),
        Body(None, ArbitraryData(None, None, None))
      ),
      Response(
        statusCode,
        ArbitraryData(None, None, None),
        Body(Some(contentType), ArbitraryData(None, Some(responseBody.noSpaces), None))
      ),
      Vector()
    )
  }
}


class BasicInterpretationsSpec extends FunSpec {
  describe("AddRequestBodyContentType") {

    implicit val shapeBuildingStrategy = ShapeBuildingStrategy.inferPolymorphism

    val builtShape = DistributionAwareShapeBuilder.toCommands(Vector(JsonLikeFrom.json(json"""{}""").get))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)
    val initialCommands = Seq(
      Commands.AddRequest("request1", "root", "PUT")
    ) ++ builtShape._2.flatten ++ Seq(
      Commands.SetRequestBodyShape("request1", ShapedBodyDescriptor("text/plain", builtShape._1, false)),
      Commands.AddResponseByPathAndMethod("response1", "root", "PUT", 200)
    )

    it("should add the expected content type to the spec") {
      val rfcState: RfcState = TestHelpers.fromCommands(initialCommands)
      val resolvers = new DefaultShapesResolvers(rfcState)

      val interaction: HttpInteraction = InteractionHelpers.simplePut(json"""999""")
      val diffs = DiffHelpers.diff(resolvers, rfcState, interaction)
      assert(diffs == Seq(
        UnmatchedRequestBodyContentType(
          InteractionTrail(Seq(Url(), Method("PUT"), RequestBody("application/json"))),
          SpecPath("root")
        )
      ))
      implicit val ids = OpticIds.newDeterministicIdGenerator
      val diff = diffs.head
      val interpretations = new BasicInterpreters(rfcState).interpret(diff, interaction)
      assert(interpretations.length == 1)
      val interpretation = interpretations.head
      println(interpretation.commands)
      val newRfcState = TestHelpers.fromCommands(initialCommands ++ interpretation.commands)
      val newDiffs = DiffHelpers.diff(resolvers, newRfcState, interaction)
      assert(newDiffs.isEmpty)
    }
  }
  describe("AddResponseBodyContentType") {
    implicit val shapeBuildingStrategy = ShapeBuildingStrategy.inferPolymorphism
    val builtShape = DistributionAwareShapeBuilder.toCommands(Vector(JsonLikeFrom.json(json"""{}""").get))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)
    val initialCommands = Seq(
      Commands.AddRequest("request1", "root", "GET")
    ) ++ builtShape._2.flatten ++ Seq(
      Commands.AddResponseByPathAndMethod("response1", "root", "GET", 200),
      Commands.SetResponseBodyShape("response1", ShapedBodyDescriptor("text/plain", builtShape._1, false)),
    )

    it("should add the expected content type to the spec") {
      val rfcState: RfcState = TestHelpers.fromCommands(initialCommands)
      val resolvers = new DefaultShapesResolvers(rfcState)

      val interaction: HttpInteraction = InteractionHelpers.simpleGet(json"""999""")
      val diffs = DiffHelpers.diff(resolvers, rfcState, interaction)
      assert(diffs == Seq(
        UnmatchedResponseBodyContentType(
          InteractionTrail(Seq(ResponseBody("application/json", 200))),
          SpecPath("root")
        )
      ))
      val diff = diffs.head
      implicit val ids = OpticIds.newDeterministicIdGenerator
      val interpretations = new BasicInterpreters(rfcState).interpret(diff, interaction)
      assert(interpretations.length == 1)
      val interpretation = interpretations.head
      println(interpretation.commands)
      val newRfcState = TestHelpers.fromCommands(initialCommands ++ interpretation.commands)
      val newDiffs = DiffHelpers.diff(resolvers, newRfcState, interaction)
      assert(newDiffs.isEmpty)
    }
  }
  describe("ChangeShape") {
    implicit val shapeBuildingStrategy = ShapeBuildingStrategy.inferPolymorphism
    describe("changing a field's shape") {
      val builtShape = DistributionAwareShapeBuilder.toCommands(Vector(JsonLikeFrom.json(json"""{"k":1}""").get))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)
      val initialCommands = Seq(
        Commands.AddRequest("request1", "root", "PUT")
      ) ++ builtShape._2.flatten ++ Seq(
        Commands.SetRequestBodyShape("request1", ShapedBodyDescriptor("application/json", builtShape._1, false)),
        Commands.AddResponseByPathAndMethod("response1", "root", "PUT", 200)
      )
      val rfcState: RfcState = TestHelpers.fromCommands(initialCommands)
      val resolvers = new DefaultShapesResolvers(rfcState)

      val interaction: HttpInteraction = InteractionHelpers.simplePut(json"""{"k":"s"}""")
      it("should work") {
        val diffs = DiffHelpers.diff(resolvers, rfcState, interaction)
        assert(diffs == Seq(
          UnmatchedRequestBodyShape(InteractionTrail(Seq(RequestBody("application/json"))), SpecRequestBody("request1"), UnmatchedShape(JsonTrail(Seq(JsonObjectKey("k"))), ShapeTrail("s_0", Seq(ObjectFieldTrail("s_1", "s_2")))))
        ))
        val diff = diffs.head
        implicit val ids = OpticIds.newDeterministicIdGenerator
        val interpretations = new BasicInterpreters(rfcState).interpret(diff, interaction)
        assert(interpretations.length == 1)
        val interpretation = interpretations.head
        val newRfcState = TestHelpers.fromCommands(initialCommands ++ interpretation.commands)
        val newDiffs = DiffHelpers.diff(resolvers, newRfcState, interaction)
        assert(newDiffs.isEmpty)
      }
    }
    describe("changing a list item's shape") {
      implicit val shapeBuildingStrategy = ShapeBuildingStrategy.inferPolymorphism
      val builtShape = DistributionAwareShapeBuilder.toCommands(Vector(JsonLikeFrom.json(json"""{"k":[1]}""").get))(OpticIds.newDeterministicIdGenerator, shapeBuildingStrategy)

      val initialCommands = Seq(
        Commands.AddRequest("request1", "root", "PUT")
      ) ++ builtShape._2.flatten ++ Seq(
        Commands.SetRequestBodyShape("request1", ShapedBodyDescriptor("application/json", builtShape._1, false)),
        Commands.AddResponseByPathAndMethod("response1", "root", "PUT", 200)
      )
      val rfcState: RfcState = TestHelpers.fromCommands(initialCommands)
      val resolvers = new DefaultShapesResolvers(rfcState)

      val interaction: HttpInteraction = InteractionHelpers.simplePut(json"""{"k":["s"]}""")
      it("should work") {
        val diffs = DiffHelpers.diff(resolvers, rfcState, interaction)
        assert(diffs == Seq(
          UnmatchedRequestBodyShape(
            InteractionTrail(Seq(RequestBody("application/json"))),
            SpecRequestBody("request1"),
            UnmatchedShape(
              JsonTrail(Seq(JsonObjectKey("k"), JsonArrayItem(0))),
              ShapeTrail("s_0", Seq(ObjectFieldTrail("s_1", "s_2"), ListItemTrail("s_2", "s_3")))
            )
          )
        ))
        val diff = diffs.head
        implicit val ids = OpticIds.newDeterministicIdGenerator
        val interpretations = new BasicInterpreters(rfcState).interpret(diff, interaction)
        assert(interpretations.length == 1)
        val interpretation = interpretations.head
        val newRfcState = TestHelpers.fromCommands(initialCommands ++ interpretation.commands)
        val newDiffs = DiffHelpers.diff(resolvers, newRfcState, interaction)
        assert(newDiffs.isEmpty)
      }
    }

  }
}
