package com.useoptic.contexts.shapes.projections

import com.useoptic.contexts.shapes.Commands.{DynamicParameterList, FieldShapeFromShape, ShapeId}
import com.useoptic.contexts.shapes.ShapesHelper._
import com.useoptic.contexts.shapes.projections.FlatShapeProjection.{FlatField, FlatShape, FlatShapeResult}
import com.useoptic.contexts.shapes.projections.NameForShapeId.ColoredComponent
import com.useoptic.contexts.shapes.{ShapesHelper, ShapesState}
import com.useoptic.diff.ShapeDiffer
import com.useoptic.diff.ShapeDiffer.resolveParameterShape
import io.circe.Json

import scala.collection.mutable
import scala.scalajs.js.annotation.JSExportAll

object ExampleProjection {

  def fromJson(json: Json, renderId: String): FlatShapeResult = {
    val result = jsonToFlatRender(json)(Seq())
    FlatShapeResult(result, Map(), Vector(), renderId)
  }

  private def flatPrimitive(kind: CoreShapeKind, value: String): FlatShape = {
    val nameComponent = ColoredComponent(value, "primitive", None, primitiveId = Some(kind.baseShapeId))
    FlatShape(kind.baseShapeId, Seq(nameComponent), Seq(), kind.baseShapeId, canName = false, Map.empty)
  }

  private def jsonToFlatRender(json: Json)(implicit path: Seq[String]): FlatShape = {

    val result = if (json.isString) {
      flatPrimitive(StringKind, json.toString)
    } else if (json.isNumber) {
      flatPrimitive(NumberKind, json.asNumber.get.toString)
    } else if (json.isBoolean) {
      flatPrimitive(BooleanKind, json.asBoolean.get.toString)
    } else if (json.isNull) {
      flatPrimitive(NullableKind, "null")
    } else if (json.isObject) {
      val fields = json.asObject.get.toList.sortBy(_._1)
      flatPrimitive(ObjectKind, "Object").copy(
        fields = fields.map(i => {
          val id = path :+ i._1
          FlatField(i._1, jsonToFlatRender(i._2)(id), id.mkString("."))
        })
      )
    } else if (json.isArray) {
      val items = json.asArray.get

      def transformItem(shape: FlatShape) = shape.copy(typeName = Seq(shape.typeName.head.copy(colorKey = "index")))

      val itemsAsFields = items.zipWithIndex.map { case (item, index) => {
        val id = path :+ s"[${index}]"
        FlatField(index.toString, transformItem(jsonToFlatRender(item)(path :+ s"[${index}]")), id.mkString("."))
      }}

      flatPrimitive(ListKind, "List").copy(
        fields = itemsAsFields
      )
    } else {
      throw new Error("Unknown JSON")
    }

    result
  }
}
