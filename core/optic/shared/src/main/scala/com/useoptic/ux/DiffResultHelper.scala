package com.useoptic.ux

import com.useoptic.contexts.requests.Commands.PathComponentId
import com.useoptic.contexts.rfc.Commands.RfcCommand
import com.useoptic.contexts.rfc.RfcState
import com.useoptic.diff.{DiffResult, InteractiveDiffInterpretation}
import com.useoptic.diff.helpers.DiffHelpers.InteractionsGroupedByDiff
import com.useoptic.diff.interactions.interpreters.{DefaultInterpreters, DiffDescription, DiffDescriptionInterpreters}
import com.useoptic.diff.interactions.{BodyUtilities, InteractionDiffResult, InteractionTrail, RequestSpecTrail, RequestSpecTrailHelpers, Resolvers, SpecPath, SpecRequestBody, SpecRequestRoot, SpecResponseBody, SpecResponseRoot, SpecRoot, UnmatchedRequestBodyContentType, UnmatchedRequestBodyShape, UnmatchedRequestMethod, UnmatchedRequestUrl, UnmatchedResponseBodyContentType, UnmatchedResponseBodyShape, UnmatchedResponseStatusCode}
import com.useoptic.diff.shapes.ShapeDiffResult
import com.useoptic.diff.shapes.resolvers.ShapesResolvers
import com.useoptic.types.capture.{Body, HttpInteraction}

import scala.scalajs.js.annotation.{JSExport, JSExportAll}

@JSExport
@JSExportAll
object DiffResultHelper {

  def unmatchedUrls(diffs: InteractionsGroupedByDiff, rfcState: RfcState): Vector[NewEndpoint] = {
    diffs.collect {
      case (newUrl: UnmatchedRequestUrl, interactions) => NewEndpoint(interactions.head.request.path, interactions.head.request.method, None, interactions.size)
      case (newMethod: UnmatchedRequestMethod, interactions) => {
        val location = getLocationForDiff(newMethod, rfcState)
        NewEndpoint(interactions.head.request.path, interactions.head.request.method, Some(location.get.pathId), interactions.size)
      }
    }
  }.toVector.sortBy(i => (i.method + i.path))

  def diffsForPathAndMethod(allEndpointDiffs: Seq[EndpointDiffs], pathId: PathComponentId, method: String, ignoredDiffs: Seq[DiffResult]): Map[InteractionDiffResult, Seq[String]] = {
    allEndpointDiffs.find(i => i.method == method && i.pathId == pathId)
      .map(i => i.diffs)
      .getOrElse(Map.empty)
  }

  def endpointDiffs(diffs: InteractionsGroupedByDiff, rfcState: RfcState): Vector[EndpointDiffs] = {
    diffs.filterNot {
      case (a: UnmatchedRequestUrl, _) => true
      case (a: UnmatchedRequestMethod, _) => true
      case _ => false
    }.flatMap {
      case (diff, interactions) => getLocationForDiff(diff, rfcState).map(location => {
        EndpointDiffs(location.method, location.pathId,  Map(diff -> interactions.map(i => i.uuid)))
      })
    }.groupBy(i => (i.pathId, i.method)).map {
      case ((path, method), diffs) => {
        val diffsForTHisOne = diffs.flatMap(_.diffs).toMap
        EndpointDiffs(method, path, diffsForTHisOne)
      }
    }
  }.toVector.sortBy(_.diffs.size).reverse

  def interactionsWithDiffsCount(diffs: Map[InteractionDiffResult, Seq[String]]): Int = diffs.flatMap(_._2).toSet.size
  def diffCount(diffs: Map[InteractionDiffResult, Seq[String]]): Int = diffs.size

  def newRegionDiffs(diffs: Map[InteractionDiffResult, Seq[String]]): Seq[NewRegionDiff] = {
    val newRegions = diffs.filterKeys {
      case _: UnmatchedRequestBodyContentType => true
      case _: UnmatchedResponseBodyContentType => true
      case _: UnmatchedResponseStatusCode => true
      case _ => false
    }

    newRegions.map{ case (_diff, _interactionPointers) => {
      val _inRequest = _diff match {
        case _:UnmatchedRequestBodyContentType => true
        case _:UnmatchedResponseBodyContentType => false
        case _:UnmatchedResponseStatusCode => false
      }
      new NewRegionDiff {
        override val diff = _diff
        override val interactionPointers: Seq[String] = _interactionPointers
        override val inRequest: Boolean = _inRequest
        override val inResponse: Boolean = !_inRequest
        override val contentType: Option[String] = _diff match {
          case d: UnmatchedRequestBodyContentType => d.interactionTrail.responseBodyContentTypeOption()
          case d: UnmatchedResponseBodyContentType => d.interactionTrail.responseBodyContentTypeOption()
          case d: UnmatchedResponseStatusCode => d.interactionTrail.responseBodyContentTypeOption()
          case _ => None
        }
        override val statusCode: Option[Int] = _diff match {
          case d: UnmatchedResponseStatusCode => Some(d.interactionTrail.statusCode())
          case d: UnmatchedResponseBodyContentType => Some(d.interactionTrail.statusCode())
          case _  => None
        }
      }
    }}.toVector.sortBy(_.statusCode)
  }

  def bodyDiffs(diffs: Map[InteractionDiffResult, Seq[String]]): Seq[BodyDiff] = {
    val bodyRegions = diffs.filterKeys {
      case _: UnmatchedRequestBodyShape => true
      case _: UnmatchedResponseBodyShape => true
      case _ => false
    }

    bodyRegions.map { case (_diff, _interactionPointers) => {
      val _inRequest = _diff match {case _: UnmatchedRequestBodyShape => true; case _ => false}
      val _inResponse = _diff match {case _: UnmatchedResponseBodyShape  => true; case _ => false}

      val _location = if (_inRequest) Seq("Request Body", _diff.interactionTrail.requestContentType()) else
        Seq(s"${_diff.interactionTrail.statusCode()} Response", "Body", _diff.interactionTrail.responseBodyContentTypeOption().getOrElse(""))

      new BodyDiff {
        override val diff = _diff
        override val location = _location
        override val interactionPointers = _interactionPointers
        override val inRequest: Boolean = _inRequest
        override val inResponse: Boolean = _inResponse
      }
    }}.toVector
  }


  case class DiffLocation(pathId: PathComponentId, method: String)
  def getLocationForDiff(diff: InteractionDiffResult, rfcState: RfcState): Option[DiffLocation] = (diff) match {
    case (_: UnmatchedRequestUrl) => None
    case (d: InteractionDiffResult) => {
      d.requestsTrail match {
        case SpecRoot() => None
        case SpecPath(pathId) => d.interactionTrail.httpMethod().flatMap(method => Some(DiffLocation(pathId, method)))
        case SpecRequestRoot(requestId) => {
          val request = rfcState.requestsState.requests(requestId).requestDescriptor
          Some(DiffLocation(request.pathComponentId, request.httpMethod))
        }
        case SpecRequestBody(requestId) => {
          val request = rfcState.requestsState.requests(requestId).requestDescriptor
          Some(DiffLocation(request.pathComponentId, request.httpMethod))
        }
        case SpecResponseRoot(responseId) => {
          val response = rfcState.requestsState.responses(responseId).responseDescriptor
          Some(DiffLocation(response.pathId, response.httpMethod))
        }
        case SpecResponseBody(responseId) => {
          val response = rfcState.requestsState.responses(responseId).responseDescriptor
          Some(DiffLocation(response.pathId, response.httpMethod))
        }
      }
    }
    case _ => None
  }

  def descriptionFromDiff(diff: InteractionDiffResult, rfcState: RfcState, anInteraction: HttpInteraction): DiffDescription = {
    val descriptionInterpreters = new DiffDescriptionInterpreters(rfcState)
    descriptionInterpreters.interpret(diff, anInteraction)
  }

  def previewDiff(bodyDiff: BodyDiff, anInteraction: HttpInteraction, currentRfcState: RfcState): Option[SideBySideRenderHelper] = {
    val simulatedDiffPreviewer = new DiffPreviewer(currentRfcState)
    val diff = bodyDiff.diff.asInstanceOf[InteractionDiffResult]
    //@todo review this interface
    def previewForBodyAndDiff(body: Body) = {
      simulatedDiffPreviewer.previewDiff(
        BodyUtilities.parseBody(body),
        Some(diff.shapeDiffResultOption.get.shapeTrail.rootShapeId),
        Set(diff.shapeDiffResultOption).flatten,
        Set.empty)
    }

    previewForBodyAndDiff(if (bodyDiff.inRequest) anInteraction.request.body else anInteraction.response.body)
  }

  def previewBody(body: Body, currentRfcState: RfcState): Option[SideBySideRenderHelper] = {
    val simulatedDiffPreviewer = new DiffPreviewer(currentRfcState)
    simulatedDiffPreviewer.previewBody(body)
  }

  def suggestionsForDiff(bodyDiff: BodyDiff, anInteraction: HttpInteraction, currentRfcState: RfcState): Seq[InteractiveDiffInterpretation] = {
    val resolvers = ShapesResolvers.newResolver(currentRfcState)
    val basicInterpreter = new DefaultInterpreters(resolvers, currentRfcState)
    basicInterpreter.interpret(bodyDiff.diff, Vector(anInteraction))
  }


}


// Helper Classes
@JSExportAll
case class NewEndpoint(path: String, method: String, pathId: Option[PathComponentId], count: Int)
@JSExportAll
case class EndpointDiffs(method: String, pathId: PathComponentId, diffs: Map[InteractionDiffResult, Seq[String]]) {
  def count = diffs.keys.size
}


@JSExportAll
abstract class NewRegionDiff {
  val diff: InteractionDiffResult
  val interactionPointers: Seq[String]
  val inRequest: Boolean
  val inResponse: Boolean
  val contentType: Option[String]
  val statusCode: Option[Int]

  def firstInteractionPointer: String = interactionPointers.head
  def interactionsCount: Int = interactionPointers.size

  def previewBodyRender(currentInteraction: HttpInteraction): Option[SideBySideRenderHelper] = {
    val body = if (inRequest) {
      currentInteraction.request.body
    } else {
      currentInteraction.response.body
    }
    new DiffPreviewer(null, null).previewBody(body)
  }

  def previewShapeRender(rfcState: RfcState, interactions: Vector[HttpInteraction], inferPolymorphism: Boolean): PreviewShapeAndCommands = {
    val diffPreviewer = new DiffPreviewer(ShapesResolvers.newResolver(rfcState), rfcState)

    def getBody(i: HttpInteraction) = {
      if (inRequest) {
        i.request.body
      } else {
        i.response.body
      }
    }

    val firstInteraction = interactions.head

    val result = if (inferPolymorphism) {
      val bodies = interactions.map(getBody).flatMap(BodyUtilities.parseBody)
      val preview = diffPreviewer.shapeOnlyFromShapeBuilder(bodies)
      preview.map(i => PreviewShapeAndCommands(Some(i._2), toSuggestion(Vector(interactions.head), rfcState, inferPolymorphism).headOption))
    } else {
      diffPreviewer.shapeOnlyFromShapeBuilder(Vector(BodyUtilities.parseBody(getBody(firstInteraction))).flatten)
        .map(i => PreviewShapeAndCommands(Some(i._2), toSuggestion(interactions, rfcState, inferPolymorphism).headOption))
    }

    result.getOrElse(PreviewShapeAndCommands(None, None))
  }


  def toSuggestion(interactions: Vector[HttpInteraction], currentRfcState: RfcState, inferPolymorphism: Boolean): Seq[InteractiveDiffInterpretation] = {
    val resolvers = ShapesResolvers.newResolver(currentRfcState)
    val basicInterpreter = new DefaultInterpreters(resolvers, currentRfcState)
    if (inferPolymorphism) {
      basicInterpreter.interpret(diff, interactions)
    } else {
      basicInterpreter.interpret(diff, interactions.head)
    }
  }

  override def toString: PathComponentId = diff.toString + interactionPointers.toString()

}

@JSExportAll
abstract class BodyDiff {
  val diff: InteractionDiffResult
  val location: Seq[String]
  val interactionPointers: Seq[String]
  val inRequest: Boolean
  val inResponse: Boolean

  override def toString: PathComponentId = diff.toString + interactionPointers.toString()

  def firstInteractionPointer: String = interactionPointers.head
  def interactionsCount: Int = interactionPointers.size
}

@JSExportAll
case class PreviewShapeAndCommands(shape: Option[ShapeOnlyRenderHelper], suggestion: Option[InteractiveDiffInterpretation])
