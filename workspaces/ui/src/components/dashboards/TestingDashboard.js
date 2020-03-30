import React, { useContext, useEffect, useState } from 'react';
import Loading from '../navigation/Loading';
import { Link, Switch, Route, Redirect } from 'react-router-dom';
import { opticEngine, Queries } from '@useoptic/domain';
import { createExampleTestingService } from '../../services/TestingService';

// TODO: find a more appropriate place for this logic to live rather than in
// Contexts now that it's being re-used elsewhere.
import {
  flattenPaths,
  fuzzyConceptFilter,
  fuzzyPathsFilter,
  flatMapOperations
} from '../../contexts/ApiOverviewContext';
import { stuffFromQueries } from '../../contexts/RfcContext';
import * as uniqBy from 'lodash.uniqby';

function DefaultReportRedirect(props) {
  const { match } = props;
  const baseUrl = match.url;

  const { loading, result: captures } = useDashboardService((service) =>
    service.listCaptures()
  );

  if (loading) {
    return <Loading />;
  }

  if (!captures) {
    // TODO: revisit this state
    return <div>Could not find any reports</div>;
  }

  let mostRecent = captures[0];
  if (mostRecent) {
    return <Redirect to={`${baseUrl}/captures/${mostRecent.captureId}`} />;
  } else {
    // TODO: revisit this UI state
    return <div>You don't have any captures yet</div>;
  }
}

function useDashboardService(
  performRequest, // Note: this is where a TS interface would give some nice safety
  deps
) {
  const service = useContext(TestingDashboardServiceContext);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    performRequest(service)
      .then((result) => {
        setResult(result);
        setLoading(false);
      })
      .catch((err) => {
        setError(err);
      });
  }, deps);

  return { result, loading, error };
}

function useSpec(captureId) {
  const { result: events, ...hookRest } = useDashboardService(
    (service) => service.loadSpec(captureId),
    [captureId]
  );

  // calling this spec instead of rfcState, to differentiate this as a ViewModel,
  // rather than RfcState.
  let spec = null;
  if (events) {
    spec = specFromEvents(events);
  }

  return { ...hookRest, result: spec };
}

const ReadonlySpecContext = React.createContext(null);
const TestingDashboardServiceContext = React.createContext(null);

// TODO: give this building of a ViewModel a more appropriate spot.
export function specFromEvents(events) {
  const { contexts } = opticEngine.com.useoptic;
  const { RfcServiceJSFacade } = contexts.rfc;
  const rfcServiceFacade = RfcServiceJSFacade();
  const eventStore = rfcServiceFacade.makeEventStore();
  const rfcId = 'testRfcId';

  // @TODO: figure out if it's wise to stop the parsing of JSON from the response, to prevent
  // parse -> stringify -> parse
  eventStore.bulkAdd(rfcId, JSON.stringify(events));
  const rfcService = rfcServiceFacade.makeRfcService(eventStore);
  const queries = Queries(eventStore, rfcService, rfcId);

  const { apiName, pathsById, requestIdsByPathId, requests } = stuffFromQueries(
    queries
  );
  const pathTree = flattenPaths('root', pathsById);
  const pathIdsFiltered = fuzzyPathsFilter(pathTree, '');
  const pathTreeFiltered = flattenPaths(
    'root',
    pathsById,
    0,
    '',
    pathIdsFiltered
  );
  const allPaths = [pathTreeFiltered, ...pathTreeFiltered.children];
  const endpoints = uniqBy(
    flatMapOperations(allPaths, { requests, requestIdsByPathId }),
    'requestId'
  ).map(({ request, path }) => ({
    endpointId: `${request.requestId}${path.pathId}`,
    request: {
      requestId: request.requestId,
      httpMethod: request.requestDescriptor.httpMethod,
      isRemoved: request.isRemoved
    },
    path
  }));

  return {
    apiName,
    endpoints
  };
}

function DashboardLoaderFactory({ serviceFactory, getBaseUrl }) {
  return function(props) {
    const { match } = props;
    // eslint-disable-next-line react-hooks/rules-of-hooks
    const [service, setService] = useState(null);
    // eslint-disable-next-line react-hooks/rules-of-hooks
    useEffect(() => {
      const task = async () => {
        const s = await serviceFactory(props);
        setService(s);
      };
      task();
    }, []);

    if (!service) {
      return <Loading />;
    }

    return (
      <TestingDashboardServiceContext.Provider value={service}>
        <Switch>
          <Route
            path={`${match.url}/captures/:captureId`}
            component={TestingDashboard}
          />
          <Route component={DefaultReportRedirect} />
        </Switch>
      </TestingDashboardServiceContext.Provider>
    );
  };
}

export function TestingDashboard(props) {
  const { captureId } = props.match.params;
  const { loading: loadingReport, result: report } = useDashboardService(
    (service) => service.loadReport(captureId),
    [captureId]
  );

  const { loading: loadingSpec, result: spec } = useSpec(captureId);

  return (
    <div>
      <h2>Live Contract Testing Dashboard for capture {captureId}</h2>

      {(loadingReport || loadingSpec) && <Loading />}

      {report && spec && <TestingReport report={report} spec={spec} />}
    </div>
  );
}

export function TestingReport(props) {
  const { report, spec } = props;
  const { counts } = report;
  const { endpoints } = spec;

  return (
    <div>
      <h3>Testing report</h3>

      <h4>Summary for {spec.apiName}</h4>
      <ul>
        <li>Created at: {report.createdAt}</li>
        <li>Last updated: {report.updatedAt}</li>
        <li>Total interactions: {counts.totalInteractions}</li>
        <li>Compliant interactions: {counts.totalCompliantInteractions}</li>
        <li>Unmatched paths: {counts.totalUnmatchedPaths}</li>
        <li>Total diffs: {counts.totalDiffs}</li>
      </ul>

      <h4>Endpoints</h4>

      {endpoints.length > 0 ? (
        <ul>
          {endpoints.map((endpoint) => (
            <li key={endpoint.request.requestId}>
              <strong>{endpoint.request.httpMethod}</strong>{' '}
              {endpoint.path.name}
            </li>
          ))}
        </ul>
      ) : (
        // @TODO: revisit this empty state
        <p>No endpoints have been documented yet</p>
      )}
    </div>
  );
}

export function ExampleTestingDashboardLoader() {
  return DashboardLoaderFactory({
    serviceFactory: (props) =>
      createExampleTestingService(props.match.params.exampleId)
  });
}
