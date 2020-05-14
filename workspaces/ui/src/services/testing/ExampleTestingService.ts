// TODO: Consider using a TypeScript interface here
// interace ITestingService
import { opticEngine, Queries } from '@useoptic/domain';
// placeholder for actual remote service
import { StableHasher } from '../../utilities/CoverageUtilities';
import { DiffManagerFacade, JsonHelper, mapScala } from '@useoptic/domain';
import { ITestingService, CoverageReport, TestingServiceError } from '.';

export async function createExampleTestingService(exampleId = 'todo-report') {
  const example = await fetch(`/example-reports/${exampleId}.json`, {
    headers: {
      accept: 'application/json',
    },
  }).then((response) => {
    if (response.ok) {
      return response.json();
    }
    throw new Error();
  });

  const { orgId, specs, samples: samplesByCaptureId, captures } = example;

  function getSpecEvents(captureId) {
    const spec = specs[captureId];
    if (typeof spec === 'string') {
      // allow specs for one capture reference other specs, to keep example json
      // under control
      return getSpecEvents(spec);
    } else {
      return spec;
    }
  }

  function getSamples(captureId) {
    const samples = samplesByCaptureId[captureId];
    if (typeof samples === 'string') {
      // allow samples for one capture reference other samples, to keep example json
      // under control
      return getSamples(samples);
    } else {
      return samples;
    }
  }

  function notFoundErr() {
    return new TestingServiceError('Not found', { statusCode: 404 });
  }

  class ExampleTestingService implements ITestingService {
    orgId: string;

    constructor(orgId) {
      this.orgId = orgId;
    }

    async loadSpecEvents(captureId) {
      await new Promise((r) => setTimeout(r, 200));

      const spec = getSpecEvents(captureId);

      if (!spec) throw notFoundErr();

      return spec;
    }

    async listCaptures() {
      await new Promise((r) => setTimeout(r, 400));

      return captures.map(
        // tolerate example resource evolving by only picking fields we need
        ({ captureId, createdAt, updatedAt, completedAt, tags }) => {
          return {
            captureId,
            createdAt,
            updatedAt,
            completedAt,
            tags: tags.map(({ name, value }) => ({
              name,
              value,
            })),
          };
        }
      );
    }

    async loadCapture(captureId) {
      await new Promise((r) => setTimeout(r, 200));
      const capture = captures.find(
        (capture) => captureId === capture.captureId
      );
      if (!capture) throw notFoundErr();

      return capture;
    }

    async loadReport(captureId) {
      await new Promise((r) => setTimeout(r, 2000));
      const events = getSpecEvents(captureId);
      const samples = getSamples(captureId);

      if (!events || !samples) throw notFoundErr();

      const { rfcState } = queriesFromEvents(events);

      const samplesSeq = JsonHelper.jsArrayToSeq(
        samples.map((x) => JsonHelper.fromInteraction(x))
      );
      const converter = new opticEngine.com.useoptic.CoverageReportConverter(
        StableHasher
      );
      const report = opticEngine.com.useoptic.diff.helpers
        .CoverageHelpers()
        .getCoverage(rfcState, samplesSeq);
      const serializedReport: CoverageReport = converter.toJs(report);
      return serializedReport;
    }

    async loadEndpointDiffs(captureId, pathId, httpMethod) {
      const events = getSpecEvents(captureId);
      const samples = getSamples(captureId);

      if (!events || !samples) throw notFoundErr();

      const { rfcState } = queriesFromEvents(events);

      const interactions = JsonHelper.jsArrayToSeq(
        samples.map((sample) => JsonHelper.fromInteraction(sample))
      );

      const diffManager = DiffManagerFacade.newFromInteractions(
        samples,
        () => {}
      );
      diffManager.updatedRfcState(rfcState);
      const endpointDiffManager = diffManager.managerForPathAndMethod(
        pathId,
        httpMethod,
        JsonHelper.jsArrayToSeq([])
      );

      const regions = endpointDiffManager.diffRegions;

      // TODO: replace this what the service will _actually_ be returning. Obviously,
      // this isn't serialised as JSON fully, and I imagine there's other details not
      // not considered.
      return {
        newRegions: JsonHelper.seqToJsArray(regions.newRegions),
        bodyDiffs: JsonHelper.seqToJsArray(regions.bodyDiffs),
      };
    }

    async loadUndocumentedEndpoints(captureId) {
      await new Promise((r) => setTimeout(r, 200));
      const events = getSpecEvents(captureId);
      const samples = getSamples(captureId);

      if (!events || !samples) throw notFoundErr();

      const { rfcState } = queriesFromEvents(events);

      const diffManager = DiffManagerFacade.newFromInteractions(
        samples,
        () => {}
      );
      diffManager.updatedRfcState(rfcState);

      const undocumentedUrlsSeq = diffManager.unmatchedUrls(true);

      return JsonHelper.seqToJsArray(undocumentedUrlsSeq).map(
        ({ method, path, pathId, count }) => {
          return {
            method,
            path,
            pathId,
            count,
          };
        }
      );
    }
  }

  return new ExampleTestingService(orgId);
}

// Might belong in a (View)Model somewhere
export function queriesFromEvents(events) {
  const { contexts } = opticEngine.com.useoptic;
  const { RfcServiceJSFacade } = contexts.rfc;
  const rfcServiceFacade = RfcServiceJSFacade();
  const eventStore = rfcServiceFacade.makeEventStore();
  const rfcId = 'testRfcId';

  // @TODO: figure out if it's wise to stop the parsing of JSON from the response, to prevent
  // parse -> stringify -> parse
  const stringifiedEvents = JSON.stringify(events);
  eventStore.bulkAdd(rfcId, stringifiedEvents);
  const rfcService = rfcServiceFacade.makeRfcService(eventStore);
  const queries = Queries(eventStore, rfcService, rfcId);
  const rfcState = rfcService.currentState(rfcId);

  return { queries, rfcState };
}
