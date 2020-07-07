import React, { useContext } from 'react';
import withStyles from '@material-ui/core/styles/withStyles';
import { Typography } from '@material-ui/core';
import compose from 'lodash.compose';
import {
  EndpointsContextStore,
  withEndpointsContext,
} from '../../../contexts/EndpointContext';
import {
  SpecServiceContext,
  useServices,
  withSpecServiceContext,
} from '../../../contexts/SpecServiceContext';
import { DiffContext, DiffContextStore, withDiffContext } from './DiffContext';
import { RfcContext, withRfcContext } from '../../../contexts/RfcContext';
import {
  DiffResultHelper,
  Facade,
  opticEngine,
  RfcCommandContext,
} from '@useoptic/domain';
import SimulatedCommandContext from '../SimulatedCommandContext';
import { Redirect } from 'react-router-dom';
import { useBaseUrl } from '../../../contexts/BaseUrlContext';
import {
  CaptureContextStore,
  useCaptureContext,
} from '../../../contexts/CaptureContext';
import { DiffReviewPage } from './DiffReviewPage';

const { diff, JsonHelper } = opticEngine.com.useoptic;
const jsonHelper = JsonHelper();

function DiffPageNew(props) {
  const { specStore } = useContext(SpecServiceContext);
  const baseUrl = useBaseUrl();
  const services = useServices();

  const { pathId, method, captureId } = props.match.params;

  return (
    <CaptureSessionInlineContext
      specStore={specStore}
      captureId={captureId}
      pathId={pathId}
      method={method}
      services={services}
    >
      <EndpointsContextStore
        pathId={pathId}
        method={method}
        inContextOfDiff={true}
        notFound={<Redirect to={`${baseUrl}/diffs`} />}
      >
        {/*<DiffPageContent captureId={captureId} />*/}
        <DiffReviewPage captureId={captureId} pathId={pathId} method={method} />
      </EndpointsContextStore>
    </CaptureSessionInlineContext>
  );
}

export const SuggestionsContext = React.createContext(null);

function SuggestionsStore({ children }) {
  const [acceptedSuggestions, setAcceptedSuggestions] = React.useState([]);

  const resetAccepted = () => {
    setAcceptedSuggestions([]);
  };

  const context = {
    acceptedSuggestions,
    setAcceptedSuggestions,
    resetAccepted,
  };
  return (
    <SuggestionsContext.Provider value={context}>
      {children}
    </SuggestionsContext.Provider>
  );
}

export const IgnoreDiffContext = React.createContext(null);

export function IgnoreDiffStore({ children }) {
  const [ignoredDiffs, setIgnoredDiffs] = React.useState([]);

  const ignoreDiff = (...diffs) => {
    setIgnoredDiffs([...ignoredDiffs, ...diffs]);
  };
  const resetIgnored = () => setIgnoredDiffs([]);

  const context = {
    ignoreDiff,
    ignoredDiffs,
    resetIgnored,
  };
  return (
    <IgnoreDiffContext.Provider value={context}>
      {children}
    </IgnoreDiffContext.Provider>
  );
}

function flatten(acc, array) {
  return [...acc, ...array];
}

const InnerDiffWrapper = function (props) {
  const { isLoading, session } = props;
  const { children } = props;
  const {
    endpointDiffs,
    updatedAdditionalCommands,
    diffId,
    completed,
  } = useCaptureContext();
  const {
    setAcceptedSuggestions,
    setSelectedDiff,
    acceptedSuggestions,
    ignoredDiffs,
    resetIgnored,
    resetAccepted,
    pathId,
    method,
  } = props;

  if (isLoading) {
    return null;
  }

  const diffsForThisEndpoint = DiffResultHelper.diffsForPathAndMethod(
    jsonHelper.jsArrayToSeq(endpointDiffs),
    pathId,
    method,
    jsonHelper.jsArrayToSeq(ignoredDiffs)
  );

  // global.opticDebug.diffContext = {
  //   samples: session ? session.samples : [],
  //   samplesSeq: jsonHelper.jsArrayToSeq(
  //     (session ? session.samples : []).map((x) =>
  //       jsonHelper.fromInteraction(x)
  //     )
  //   ),
  //   // diffResults,
  //   acceptedSuggestions,
  //   suggestionToPreview,
  //   // regions,
  //   // getInteractionsForDiff,
  //   // interpreter,
  //   // interpretationsForDiffAndInteraction,
  //   simulatedCommands,
  //   eventStore,
  //   initialEventStore,
  //   rfcState,
  //   opticEngine,
  //   StableHasher,
  // };
  /*
const converter = new opticDebug.diffContext.opticEngine.com.useoptic.CoverageReportConverter(opticDebug.diffContext.StableHasher)
const report = opticDebug.diffContext.opticEngine.com.useoptic.diff.helpers.CoverageHelpers().getCoverage(opticDebug.diffContext.rfcState, opticDebug.diffContext.samples)
converter.toJs(report)
 */

  return (
    <DiffContextStore
      diffId={diffId}
      diffsForThisEndpoint={diffsForThisEndpoint}
      completed={completed}
      reset={() => {
        updatedAdditionalCommands([]);
        resetIgnored();
        resetAccepted();
      }}
      acceptSuggestion={(...suggestions) => {
        if (suggestions) {
          const updatedSuggestions = [...acceptedSuggestions, ...suggestions];
          setAcceptedSuggestions(updatedSuggestions);
          const simulatedCommands = updatedSuggestions
            .map((x) => jsonHelper.seqToJsArray(x.commands))
            .reduce(flatten, []);
          updatedAdditionalCommands(simulatedCommands);
        }
      }}
      acceptedSuggestions={acceptedSuggestions}
    >
      {children}
    </DiffContextStore>
  );
  // })
};

class _CaptureSessionInlineContext extends React.Component {
  render() {
    const jsonHelper = JsonHelper();
    const {
      captureId,
      services,
      rfcId,
      eventStore,
      children,
      pathId,
      method,
    } = this.props;
    return (
      //@todo refactor sessionId to captureId
      <SuggestionsStore>
        <IgnoreDiffContext.Consumer>
          {({ ignoredDiffs, resetIgnored }) => (
            <SuggestionsContext.Consumer>
              {(suggestionsContext) => {
                const {
                  acceptedSuggestions,
                  setAcceptedSuggestions,
                  resetAccepted,
                } = suggestionsContext;
                const simulatedCommands = acceptedSuggestions
                  .map((x) => jsonHelper.seqToJsArray(x.commands))
                  .reduce(flatten, []);
                return (
                  <SimulatedCommandContext
                    rfcId={rfcId}
                    eventStore={eventStore.getCopy(rfcId)}
                    commands={simulatedCommands}
                    shouldSimulate={true}
                  >
                    <CaptureContextStore
                      captureId={captureId}
                      pathId={pathId}
                      method={method}
                      ignoredDiffs={ignoredDiffs}
                      {...services}
                    >
                      <InnerDiffWrapper
                        pathId={pathId}
                        method={method}
                        ignoredDiffs={ignoredDiffs}
                        resetIgnored={resetIgnored}
                        resetAccepted={resetAccepted}
                        setAcceptedSuggestions={setAcceptedSuggestions}
                        acceptedSuggestions={acceptedSuggestions}
                      >
                        {children}
                      </InnerDiffWrapper>
                    </CaptureContextStore>
                  </SimulatedCommandContext>
                );
              }}
            </SuggestionsContext.Consumer>
          )}
        </IgnoreDiffContext.Consumer>
      </SuggestionsStore>
    );
  }
}

const CaptureSessionInlineContext = compose(withRfcContext)(
  _CaptureSessionInlineContext
);

export default compose(withSpecServiceContext)(DiffPageNew);
