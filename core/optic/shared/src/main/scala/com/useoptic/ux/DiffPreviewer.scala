package com.useoptic.ux

import com.useoptic.contexts.rfc.RfcState
import com.useoptic.contexts.shapes.Commands.{DynamicParameterList, FieldId, ShapeId}
import com.useoptic.contexts.shapes.{FlattenedField, ShapeEntity, ShapesHelper}
import com.useoptic.contexts.shapes.ShapesHelper.{AnyKind, BooleanKind, ListKind, NullableKind, NumberKind, ObjectKind, OneOfKind, OptionalKind, StringKind, UnknownKind}
import com.useoptic.diff.DiffResult
import com.useoptic.diff.interactions.BodyUtilities
import com.useoptic.diff.interactions.interpreters.DiffDescription
import com.useoptic.diff.shapes.{ArrayVisitor, GenericWrapperVisitor, JsonLikeTraverser, JsonLikeVisitors, JsonTrail, ListShapeVisitor, ObjectShapeVisitor, ObjectVisitor, OneOfVisitor, PrimitiveShapeVisitor, PrimitiveVisitor, Resolvers, ShapeDiffResult, ShapeTrail, ShapeTraverser, ShapeVisitors, UnmatchedShape}
import com.useoptic.diff.shapes.JsonTrailPathComponent.{JsonArrayItem, JsonObjectKey}
import com.useoptic.diff.shapes.Resolvers.{ParameterBindings, ResolvedTrail}
import com.useoptic.dsa.SequentialIdGenerator
import com.useoptic.logging.Logger
import com.useoptic.types.capture.{Body, JsonLike}
import io.circe.{Json, JsonObject}

import scala.scalajs.js.annotation.{JSExport, JSExportAll}
import scala.util.Try

@JSExport
@JSExportAll
object DiffPreviewer {

  def previewDiff(jsonLike: Option[JsonLike], spec: RfcState, shapeId: ShapeId, diffs: Set[ShapeDiffResult]): RenderShapeRoot = {
    import com.useoptic.diff.shapes.JsonLikeTraverser
    //    val shapeRenderVisitor = new ShapeRenderVisitor(spec)
    //first traverse the example
    val exampleRenderVisitor = new ExampleRenderVisitor(spec, diffs)
    val jsonLikeTraverser = new JsonLikeTraverser(spec, exampleRenderVisitor)
    jsonLikeTraverser.traverse(jsonLike, JsonTrail(Seq.empty), Some(ShapeTrail(shapeId, Seq.empty)))

    //second traversal of spec with examples
    val shapeRenderVisitor = new ShapeRenderVisitor(spec, diffs, exampleRenderVisitor)
    val specTraverser = new ShapeTraverser(spec, shapeRenderVisitor)
    specTraverser.traverse(shapeId, ShapeTrail(shapeId, Seq()))

    RenderShapeRoot(shapeId,
      exampleRenderVisitor.fields, shapeRenderVisitor.fields,
      exampleRenderVisitor.shapes, shapeRenderVisitor.shapes,
      exampleRenderVisitor.items, shapeRenderVisitor.items
    )
  }

  def previewJson(jsonLike: JsonLike): RenderShapeRoot = {
    val exampleRenderVisitor = new ExampleRenderVisitor(RfcState.empty, Set.empty)
    val jsonLikeTraverser = new JsonLikeTraverser(RfcState.empty, exampleRenderVisitor)

    jsonLikeTraverser.traverse(Some(jsonLike), JsonTrail(Seq.empty), None)
    Logger.log(exampleRenderVisitor.shapes)
    RenderShapeRoot(
      exampleRenderVisitor.rootShape.shapeId,
      exampleRenderVisitor.fields, Map.empty,
      exampleRenderVisitor.shapes, Map.empty,
      exampleRenderVisitor.items, Map.empty
    )
  }

  def previewBody(body: Body): Option[RenderShapeRoot] = {
    BodyUtilities.parseBody(body).map(previewJson)
  }

  def previewShape(spec: RfcState, shapeIdOption: Option[ShapeId]): Option[RenderShapeRoot] = shapeIdOption map { shapeId =>
    val exampleRenderVisitor = new ExampleRenderVisitor(spec, Set.empty)
    val shapeRenderVisitor = new ShapeRenderVisitor(spec, Set.empty, exampleRenderVisitor)
    val specTraverser = new ShapeTraverser(spec, shapeRenderVisitor)
    specTraverser.traverse(shapeId, ShapeTrail(shapeId, Seq()))
    RenderShapeRoot(shapeId,
      exampleRenderVisitor.fields, shapeRenderVisitor.fields,
      exampleRenderVisitor.shapes, shapeRenderVisitor.shapes,
      exampleRenderVisitor.items, shapeRenderVisitor.items
    )
  }

}

class ExampleRenderVisitor(spec: RfcState, diffs: Set[ShapeDiffResult]) extends JsonLikeVisitors with RenderVisitor {

  def diffsByTrail(bodyTrail: JsonTrail): Set[DiffResult] = {
    diffs.collect {
      case sd: ShapeDiffResult if sd.jsonTrail == bodyTrail => sd
    }
  }

  override val objectVisitor: ObjectVisitor = new ObjectVisitor {
    override def begin(value: Map[String, JsonLike], bodyTrail: JsonTrail, expected: ResolvedTrail, shapeTrail: ShapeTrail): Unit = {

      def idFromName(name: String) = bodyTrail.withChild(JsonObjectKey(name)).toString

      val fieldNameToId = expected.shapeEntity.descriptor.fieldOrdering
        .map(fieldId => {
          val field = spec.shapesState.fields(fieldId)
          val fieldShape = Resolvers.resolveFieldToShape(spec.shapesState, fieldId, expected.bindings).flatMap(x => {
            Some(x.shapeEntity)
          }).get
          (field.descriptor.name -> (idFromName(field.descriptor.name), field, fieldShape))
        }).toMap

      val knownFieldsIds = fieldNameToId.values.map(_._1)
      val missingFieldIds = fieldNameToId.flatMap(entry => {
        val (fieldName, (fieldId, field, fieldShape)) = entry
        val fieldTrail = bodyTrail.withChild(JsonObjectKey(fieldName))
        if (!value.contains(fieldName)) {
          pushField(RenderField(fieldTrail.toString, Some(field.fieldId), fieldName, Some(fieldShape.shapeId), value.get(fieldName).map(_.asJson), diffs = diffsByTrail(fieldTrail)))
          Some(fieldId)
        } else None
      })

      val extraFieldIds = value.flatMap { case (key, value) => {
        if (!fieldNameToId.contains(key)) {
          Logger.log(s"object has extra field ${key}")
          val fieldTrail = bodyTrail.withChild(JsonObjectKey(key))
          val extraFieldId = fieldTrail.toString
          pushField(RenderField(extraFieldId, None, key, Some(extraFieldId), Some(value.asJson), diffs = diffsByTrail(fieldTrail)))
          Some(extraFieldId)
        } else None
      }
      }


      val fields = Fields(
        expected = knownFieldsIds.toSeq,
        missing = missingFieldIds.toSeq,
        unexpected = extraFieldIds.toSeq
      )

      pushShape(
        RenderShape(bodyTrail.toString, Some(expected.shapeEntity.shapeId), ObjectKind.baseShapeId, fields,
          diffs = diffsByTrail(bodyTrail),
          exampleValue = Some(Json.fromJsonObject(JsonObject.fromMap(value.mapValues(_.asJson))))
        )
      )
    }

    override def beginUnknown(value: Map[String, JsonLike], bodyTrail: JsonTrail): Unit = {
      val objectId = bodyTrail.toString
      val fieldIds = value.map{ case (key, value) => {
        val fieldShapeId = bodyTrail.withChild(JsonObjectKey(key)).toString
        pushField(RenderField(fieldShapeId, None, key, Some(fieldShapeId), Some(value.asJson)))
        fieldShapeId
      }}.toSeq

      pushShape(RenderShape(objectId, None, ObjectKind.baseShapeId, Fields(fieldIds, Seq.empty, Seq.empty), diffs = diffsByTrail(bodyTrail), exampleValue = Some(Json.fromJsonObject(JsonObject.fromMap(value.mapValues(_.asJson))))))
    }

    override def visit(key: String, jsonLike: JsonLike, bodyTrail: JsonTrail, trail: Option[ShapeTrail], parentBindings: ParameterBindings): Unit = {
      //only map known fields here
      if (trail.isDefined) {
        val fieldEntity = trail.get.lastField().flatMap(i => spec.shapesState.fields.get(i)).get
        val fieldShape = Resolvers.resolveFieldToShape(spec.shapesState, fieldEntity.fieldId, parentBindings)
        pushField(RenderField(
          bodyTrail.toString,
          Some(fieldEntity.fieldId),
          key,
          fieldShape.map(_.shapeEntity.shapeId),
          Some(jsonLike.asJson),
          diffs = diffsByTrail(bodyTrail.withChild(JsonObjectKey(key)))
        ))
      }
    }

    override def end(): Unit = {}
  }
  override val arrayVisitor: ArrayVisitor = new ArrayVisitor {

    override def beginUnknown(value: Vector[JsonLike], bodyTrail: JsonTrail): Unit = {
      val arrayId = bodyTrail.toString
      val ids = value.zipWithIndex.map {
        case (i, index) => {
          val id = bodyTrail.withChild(JsonArrayItem(index)).toString
          pushItem(RenderItem(
            id,
            index.intValue(),
            Resolvers.jsonToCoreKind(i).baseShapeId,
            Some(bodyTrail.withChild(JsonArrayItem(index)).toString), //simulate the id of the child
            Some(i.asJson)
          ))
          id
        }
      }

      pushShape(RenderShape(
        arrayId,
        None,
        ListKind.baseShapeId,
        items = Items(ids),
        exampleValue = Some(Json.fromValues(value.map(_.asJson))),
        diffs = diffsByTrail(bodyTrail)
      ))
    }

    override def begin(value: Vector[JsonLike], bodyTrail: JsonTrail, shapeTrail: ShapeTrail, resolvedShapeTrail: ResolvedTrail): Unit = {

      val ids = value.zipWithIndex.map {
        case (i, index) => bodyTrail.withChild(JsonArrayItem(index)).toString
      }

      pushShape(RenderShape(
        bodyTrail.toString,
        Some(resolvedShapeTrail.shapeEntity.shapeId),
        ListKind.toString,
        items = Items(ids),
        exampleValue = Some(Json.fromValues(value.map(_.asJson))),
        diffs = diffsByTrail(bodyTrail)
      ))
    }

    override def visit(index: Number, value: JsonLike, bodyTrail: JsonTrail, trail: Option[ShapeTrail]): Unit = {

      trail.foreach(shapeTrail => {
        val lastListItem = shapeTrail.lastListItem().get
        pushItem(RenderItem(
          bodyTrail.toString,
          index.intValue(),
          Resolvers.jsonToCoreKind(value).baseShapeId,
          Some(lastListItem.itemShapeId),
          Some(value.asJson),
          diffs = diffsByTrail(bodyTrail)
        ))
      })
    }

    override def end(): Unit = {

    }
  }
  override val primitiveVisitor: PrimitiveVisitor = new PrimitiveVisitor {
    override def visit(value: Option[JsonLike], bodyTrail: JsonTrail, trail: Option[ShapeTrail]): Unit = {
      val resolvedTrailOption = Try(Resolvers.resolveTrailToCoreShape(spec, trail.get)).toOption
      if (value.isDefined) {
        if (resolvedTrailOption.isDefined) {
          val shape = resolvedTrailOption.get.shapeEntity
          val baseShapeId = resolvedTrailOption.get.coreShapeKind.baseShapeId
          pushShape(RenderShape(bodyTrail.toString, Some(shape.shapeId), baseShapeId, exampleValue = value.map(_.asJson), diffs = diffsByTrail(bodyTrail)))
        }
      }
    }

    override def visitUnknown(value: Option[JsonLike], bodyTrail: JsonTrail): Unit = {
      if (value.isDefined) {
        val baseShapeId = Resolvers.jsonToCoreKind(value.get).baseShapeId
        pushShape(RenderShape(bodyTrail.toString, None, baseShapeId, exampleValue = value.map(_.asJson), diffs = diffsByTrail(bodyTrail)))
      }
    }
  }
}

class ShapeRenderVisitor(spec: RfcState, diffs: Set[ShapeDiffResult], exampleVisitor: ExampleRenderVisitor) extends ShapeVisitors with RenderVisitor {

  def diffsByTrail(shapeTrail: ShapeTrail): Set[DiffResult] = {
    diffs.collect {
      case sd: ShapeDiffResult if sd.shapeTrail == shapeTrail => sd
    }
  }

  private val exampleFields = exampleVisitor.fields
  private val exampleShapes = exampleVisitor.shapes
  private val exampleItems = exampleVisitor.items

  override val objectVisitor: ObjectShapeVisitor = new ObjectShapeVisitor {
    override def begin(objectResolved: ResolvedTrail, shapeTrail: ShapeTrail, exampleJson: Option[JsonLike]): Unit = {

      val expectedShapeIds = objectResolved.shapeEntity.descriptor.fieldOrdering.flatMap(fieldId => {
        val field = spec.shapesState.fields(fieldId)
        if (field.isRemoved) {
          None
        } else {
          //@GOTCHA need field bindings?
          val fieldShape = Resolvers.resolveFieldToShape(spec.shapesState, fieldId, objectResolved.bindings).get
          Some(fieldId)
        }
      })

      //add missing fields from the example
      val fieldsFromExample = exampleShapes.get(objectResolved.shapeEntity.shapeId).map(i => i.fields).getOrElse(Fields(Seq.empty, Seq.empty, Seq.empty, Seq.empty))
      val joined = (fieldsFromExample.unexpected)
      exampleFields.filterKeys(i => joined.contains(i)).foreach(i => pushField(i._2))

      //push root object
      pushShape(RenderShape(
        objectResolved.shapeEntity.shapeId,
        None,
        ObjectKind.baseShapeId,
        Fields(expectedShapeIds, fieldsFromExample.missing, fieldsFromExample.unexpected, fieldsFromExample.hidden),
        Items(Seq.empty),
        diffs = diffsByTrail(shapeTrail),
        exampleValue = None,
        name = RenderName(Seq(NameComponent(ObjectKind.name, ObjectKind.color)))
      ))

    }

    override def visit(key: String, fieldId: FieldId, fieldShapeTrail: ResolvedTrail, fieldTrail: ShapeTrail): Unit = {
      val example = exampleFields.get(fieldId).flatMap(_.exampleValue)
      pushField(RenderField(
        fieldId,
        None,
        key,
        Some(fieldShapeTrail.shapeEntity.shapeId),
        example,
        diffs = diffsByTrail(fieldTrail)
      ))
    }

    override def end(): Unit = ???

  }
  override val listVisitor: ListShapeVisitor = new ListShapeVisitor {

    override def begin(shapeTrail: ShapeTrail, listShape: ShapeEntity, itemShape: ShapeEntity): Unit = {

      val id = listShape.shapeId + "_$listItem"

      val baseItem = Resolvers.resolveToBaseShape(itemShape.shapeId)(spec.shapesState)
      pushItem(RenderItem(id, 0, baseItem.descriptor.baseShapeId, Some(baseItem.shapeId), None, Set.empty))

      pushShape(
        RenderShape(
          listShape.shapeId,
          None,
          ListKind.baseShapeId,
          items = Items(Seq(id)),
          diffs = diffsByTrail(shapeTrail),
          exampleValue = None,
          name = RenderName(Seq(NameComponent("List of ", ListKind.color, inner = Some(baseItem.shapeId)))))
      )
    }

    override def end(): Unit = ???


    override def visit(): Unit = ???
  }
  override val primitiveVisitor: PrimitiveShapeVisitor = new PrimitiveShapeVisitor {
    override def visit(objectResolved: ResolvedTrail, shapeTrail: ShapeTrail): Unit = {

      val name = {
        objectResolved.coreShapeKind match {
          case ShapesHelper.AnyKind => RenderName(Seq(NameComponent(AnyKind.name, AnyKind.color)))
          case ShapesHelper.StringKind => RenderName(Seq(NameComponent(StringKind.name, StringKind.color)))
          case ShapesHelper.NumberKind => RenderName(Seq(NameComponent(NumberKind.name, NumberKind.color)))
          case ShapesHelper.BooleanKind => RenderName(Seq(NameComponent(BooleanKind.name, BooleanKind.color)))
          case _ => RenderName(Seq.empty)
        }
      }

      pushShape(RenderShape(
        objectResolved.shapeEntity.shapeId,
        None,
        objectResolved.coreShapeKind.baseShapeId,
        diffs = diffsByTrail(shapeTrail),
        exampleValue = None,
        name = name
      ))
    }
  }
  override val oneOfVisitor: OneOfVisitor = new OneOfVisitor {
    override def begin(shapeTrail: ShapeTrail, oneOfShape: ShapeEntity, branches: Seq[ShapeId]): Unit = {

      val nameComponents = branches.map(branch => {
        NameComponent(if (branches.lastOption.contains(branch) && branches.size > 1) "or " else "", "modifier", endText = if (branches.lastOption.contains(branch)) "" else ", ", inner = Some(branch))
      })

      pushShape(RenderShape(
        oneOfShape.shapeId,
        None,
        OneOfKind.baseShapeId,
        branches = branches,
        diffs = diffsByTrail(shapeTrail),
        exampleValue = None,
        name = RenderName(nameComponents)
      ))
    }

    override def visit(shapeTrail: ShapeTrail, oneOfShape: ShapeEntity, branchShape: ShapeEntity): Unit = {
    }

    override def end(): Unit = ???
  }
  override val optionalVisitor: GenericWrapperVisitor = new GenericWrapperVisitor {
    override def begin(shapeTrail: ShapeTrail, shape: ShapeEntity, innerShape: Option[ShapeEntity]): Unit = {
      pushShape(RenderShape(
        shape.shapeId,
        None,
        OptionalKind.baseShapeId,
        innerId = innerShape.map(_.shapeId),
        diffs = diffsByTrail(shapeTrail),
        exampleValue = None,
        name = RenderName(Seq(NameComponent("", "modifier", "(optional)", innerShape.map(_.shapeId))))
      ))
    }
  }
  override val nullableVisitor: GenericWrapperVisitor = new GenericWrapperVisitor {
    override def begin(shapeTrail: ShapeTrail, shape: ShapeEntity, innerShape: Option[ShapeEntity]): Unit = {
      pushShape(RenderShape(
        shape.shapeId,
        None,
        NullableKind.baseShapeId,
        innerId = innerShape.map(_.shapeId),
        diffs = diffsByTrail(shapeTrail),
        exampleValue = None,
        name = RenderName(Seq(NameComponent("", "modifier", "(nullable)", innerShape.map(_.shapeId))))
      ))
    }
  }
}


trait RenderVisitor {
  private val _fields = scala.collection.mutable.Map[String, RenderField]()
  private val _shapes = scala.collection.mutable.Map[String, RenderShape]()
  private val _items = scala.collection.mutable.Map[String, RenderItem]()

  private var _firstShape: Option[RenderShape] = None

  def rootShape: RenderShape = _firstShape.get

  def fields: Map[String, RenderField] = _fields.toMap

  def pushField(renderField: RenderField): Unit = _fields.put(renderField.fieldId, renderField)

  def shapes: Map[String, RenderShape] = _shapes.toMap

  def pushShape(renderShape: RenderShape): Unit = {
    if (_shapes.isEmpty) {
      _firstShape = Some(renderShape)
    }
    _shapes.put(renderShape.shapeId, renderShape)
  }

  def items: Map[String, RenderItem] = _items.toMap

  def pushItem(renderItem: RenderItem): Unit = _items.put(renderItem.itemId, renderItem)
}
