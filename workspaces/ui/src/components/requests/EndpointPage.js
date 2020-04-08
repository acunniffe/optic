import React from 'react';
import withStyles from '@material-ui/core/styles/withStyles';
import {DocGrid} from './DocGrid';
import {AppBar, Typography} from '@material-ui/core';
import {DocSubGroup} from './DocSubGroup';
import {DocParameter} from './DocParameter';
import {HeadingContribution, MarkdownContribution} from './DocContribution';
import {EndpointOverviewCodeBox} from './DocCodeBox';
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import compose from 'lodash.compose';
import {DocResponse} from './DocResponse';
import Collapse from '@material-ui/core/Collapse';
import {DocRequest} from './DocRequest';
import {withRfcContext} from '../../contexts/RfcContext';
import {asPathTrail, getNameWithFormattedParameters, isPathParameter} from '../utilities/PathUtilities';
import {updateContribution} from '../../engine/routines';
import sortBy from 'lodash.sortby';
import Button from '@material-ui/core/Button';
import Toolbar from '@material-ui/core/Toolbar';
import {withNavigationContext} from '../../contexts/NavigationContext';
import groupby from 'lodash.groupby';
import {BODY_DESCRIPTION, DESCRIPTION, PURPOSE} from '../../ContributionKeys';
import {NamerStore} from '../shapes/Namer';
import {getNormalizedBodyDescriptor} from '../../utilities/RequestUtilities';
import {DocQueryParams} from './DocQueryParams';
import {extractRequestAndResponseBodyAsJs} from '@useoptic/domain';
import IconButton from '@material-ui/core/IconButton';
import VerticalSplitIcon from '@material-ui/icons/VerticalSplit';
import TimelineIcon from '@material-ui/icons/Timeline';
import {LightTooltip} from '../tooltips/LightTooltip';
import Badge from '@material-ui/core/Badge';
import {EndpointsContextStore, EndpointsContext, withEndpointsContext} from '../../contexts/EndpointContext';

const styles = theme => ({
  root: {
    paddingTop: 45,
    paddingLeft: 22,
    paddingRight: 22,
    paddingBottom: 200
  },
  wrapper: {
    padding: 22,
    display: 'flex',
    width: '95%',
    marginTop: 22,
    marginBottom: 140,
    flexDirection: 'column',
    height: 'fit-content',
  },
  docButton: {
    paddingLeft: 9,
    borderLeft: '3px solid #e2e2e2',
    marginBottom: 6,
    cursor: 'pointer',
    fontWeight: 500,
  },
  showMore: {
    marginTop: 44
  },
  appBar: {
    borderBottom: '1px solid #e2e2e2',
    backgroundColor: 'white'
  },
  scroll: {
    overflow: 'scroll',
    paddingBottom: 300,
    paddingTop: 20,
  },
  container: {
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    height: '100vh'
  },
});

class EndpointPageDataLoader extends React.Component {

  state = {
    examples: []
  };

  componentDidMount() {
    const {specService, requestId} = this.props;
    if (specService) {
      specService.listExamples(requestId)
        .then(({examples}) => {
          //take from the end
          this.setState({examples: examples.reverse()});
        })
        .catch(e => {
          console.error(e);
        });
    }
  }

  requestBodyExample() {
    const [first] = this.state.examples;
    if (first) {
      return extractRequestAndResponseBodyAsJs(first).requestBody;
    }
  }

  responseExamples() {
    const grouped = groupby(this.state.examples, (e) => e.response.statusCode);
    return (statusCode) => {
      const [first] = grouped[statusCode] || [];
      if (first) {
        return extractRequestAndResponseBodyAsJs(first).responseBody;
      }
    };
  }

  render() {

    const {requestId, showShapesFirst, classes, handleCommand, cachedQueryResults, queries} = this.props;

    const {requests, pathsById, responses, contributions, requestParameters} = cachedQueryResults;

    const request = requests[requestId];

    if (!request) {
      return <div>Request not found</div>;
    }

    const {requestDescriptor} = request;
    const {httpMethod, pathComponentId, bodyDescriptor} = requestDescriptor;

    //Path
    const pathTrail = asPathTrail(pathComponentId, pathsById);
    const pathTrailComponents = pathTrail.map(pathId => pathsById[pathId]);
    const pathTrailWithNames = pathTrailComponents.map((pathComponent) => {
      const pathComponentName = getNameWithFormattedParameters(pathComponent);
      const pathComponentId = pathComponent.pathId;
      return {
        pathComponentName,
        pathComponentId
      };
    });

    const fullPath = pathTrailWithNames.map(({pathComponentName}) => pathComponentName)
      .join('/');

    const pathParameters = pathTrail
      .map(pathId => pathsById[pathId])
      .filter((p) => isPathParameter(p))
      .map(p => ({
        pathId: p.pathId,
        name: p.descriptor.ParameterizedPathComponentDescriptor.name,
        description: contributions.getOrUndefined(p.pathId, DESCRIPTION)
      }));

    // Request Body
    const requestBody = getNormalizedBodyDescriptor(bodyDescriptor);

    // Responses
    const responsesForRequest = Object.values(responses)
      .filter((response) => response.responseDescriptor.requestId === requestId);

    const parametersForRequest = Object.values(requestParameters)
      .filter((requestParameter) => requestParameter.requestParameterDescriptor.requestId === requestId);

    // const headerParameters = parametersForRequest.filter(x => x.requestParameterDescriptor.location === 'header');
    const queryParameter = parametersForRequest.filter(x => x.requestParameterDescriptor.location === 'query')[0];

    const queryString = queryParameter ? ({
      shapeId: queryParameter.requestParameterDescriptor.shapeDescriptor.ShapedRequestParameterShapeDescriptor.shapeId,
      flatShape: queries.flatShapeForShapeId(queryParameter.requestParameterDescriptor.shapeDescriptor.ShapedRequestParameterShapeDescriptor.shapeId, [])
    }) : null;

    return (
      <div className={classes.wrapper}>
        <NamerStore disable={true}>
          <EndpointPage
            endpointPurpose={contributions.getOrUndefined(requestId, PURPOSE)}
            endpointDescription={contributions.getOrUndefined(requestId, DESCRIPTION)}
            requestId={requestId}
            updateContribution={(id, key, value) => {
              handleCommand(updateContribution(id, key, value));
            }}
            getContribution={(id, key) => contributions.getOrUndefined(id, key)}
            showShapesFirst={showShapesFirst}
            method={httpMethod}
            requestBody={requestBody}
            queryString={queryString}
            requestBodyExample={this.requestBodyExample()}
            responses={sortBy(responsesForRequest, (res) => res.responseDescriptor.httpStatusCode)}
            responseExamples={this.responseExamples()}
            url={fullPath}
            parameters={pathParameters}
          />
        </NamerStore>
      </div>
    );
  }
}

export const EndpointPageWithQuery = compose(withStyles(styles), withRfcContext)(EndpointPageDataLoader);

class _EndpointPage extends React.Component {

  state = {
    showAllResponses: false
  };


  render() {
    const {
      classes,
      endpointPurpose,
      requestBody,
      responses,
      endpointDescription,
      method,
      url,
      queryString,
      parameters = [],
      getContribution,
      updateContribution,
      requestId,
      requestBodyExample,
      responseExamples,
      showShapesFirst
    } = this.props;

    const endpointOverview = (() => {
      const left = (
        <div>
          <HeadingContribution
            value={endpointPurpose}
            label="What does this endpoint do?"
            onChange={(value) => {
              updateContribution(requestId, PURPOSE, value);
            }}
          />
          <div style={{marginBottom: 6}}>
            <MarkdownContribution
              value={endpointDescription}
              label="Detailed Description"
              onChange={(value) => {
                updateContribution(requestId, DESCRIPTION, value);
              }}/>
          </div>

          {parameters.length ? (
            <DocSubGroup title="Path Parameters">
              {parameters.map(i => <DocParameter title={i.name}
                                                 paramId={i.pathId}
                                                 updateContribution={updateContribution}
                                                 description={i.description}/>)}
            </DocSubGroup>
          ) : null}
        </div>
      );

      const right = <EndpointOverviewCodeBox method={method} url={url}/>;

      return <DocGrid left={left} right={right}/>;
    })();

    const queryParameters = <DocQueryParams {...queryString}
                                            updateContribution={updateContribution}
                                            getContribution={getContribution}/>;

    const requestBodyRender = (() => {
      if (!requestBody) {
        return;
      }
      const {httpContentType, shapeId, isRemoved} = requestBody;
      if (Object.keys(requestBody).length && !isRemoved) {
        return (
          <DocRequest
            description={getContribution(requestId, BODY_DESCRIPTION)}
            contentType={httpContentType}
            shapeId={shapeId}
            requestId={requestId}
            updateContribution={updateContribution}
            showShapesFirst={showShapesFirst}
            example={requestBodyExample}
          />
        );
      }
    })();

    return (
      <div className={classes.root}>
        {endpointOverview}
        {queryParameters}

        <div style={{marginTop: 65, marginBottom: 65}}/>
        {requestBodyRender}\
      </div>
    );
  }
}

export const EndpointPage = withStyles(styles)(_EndpointPage);
