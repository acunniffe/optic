import React from 'react'
import {makeStyles} from '@material-ui/core/styles';
import {primary} from '../../../../theme';
import {ShapeExpandedContext, withShapeRenderContext} from './ShapeRenderContext';
import withStyles from '@material-ui/core/styles/withStyles';
import Tooltip from '@material-ui/core/Tooltip';
import {useContext} from 'react';

export const useShapeViewerStyles = makeStyles(theme => ({
  root: {
    paddingTop: 10,
    paddingBottom: 10,
    backgroundColor: '#16203F'
  },
  row: {
    display: 'flex',
    padding: 0,
    paddingLeft: 4,
    flexDirection: 'row',
  },
  rowWithHover: {
    cursor: 'pointer',
    '&:hover': {
      backgroundColor: 'rgba(78,165,255,0.27) !important',
    },
  },
  menu: {
    userSelect: 'none'
  },
  suggestion: {
    fontStyle: 'italic',
    color: 'white',
    flex: 1,
    textAlign: 'right',
    paddingRight: 10
  },
  hiddenItem: {
    color: '#070707',
    fontSize: 10,
    paddingLeft: 7,
    paddingRight: 7,
    backgroundColor: '#ababab',
    borderRadius: 12
  },
  symbols: {
    color: '#cfcfcf',
    fontWeight: 800,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  value: {
    fontWeight: 600,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  fieldName: {
    fontWeight: 600,
    color: '#cfcfcf',
    fontSize: 12,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  indexMarker: {
    fontWeight: 500,
    color: '#9cdcfe',
    fontSize: 12,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  rowContents: {
    display: 'flex',
    alignItems: 'baseline',
    justifyContent: 'center',
    flexDirection: 'row'
  },
  left: {
    flex: 1,
    display: 'flex',
    paddingTop: 3,
    paddingBottom: 3,
    paddingLeft: 5
  },
  spacerBorder: {
    maxWidth: 1,
    backgroundColor: '#4B5A8C'
  },
  right: {
    display: 'flex',
    paddingLeft: 5,
    paddingTop: 3,
    paddingBottom: 3,
    flex: 1,
  },
  typeName: {
    display: 'flex',
    whiteSpace: 'pre',
    flex: 1,
    fontWeight: 600,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  assertionMet: {
    display: 'flex',
    whiteSpace: 'pre',
    flex: 1,
    fontWeight: 400,
    color: '#646464',
    fontStyle: 'italic',
    fontFamily: '\'Source Code Pro\', monospace'
  },
  diffAssertion: {
    color: '#f8edf4',
    flex: 1,
    fontSize: 14,
    fontWeight: 800,
    fontFamily: '\'Source Code Pro\', monospace'
  },
  toolbar: {
    alignItems: 'flex-start',
    paddingTop: theme.spacing(1),
    paddingBottom: theme.spacing(2),
  },
  dash: {
    fontWeight: 500,
    marginLeft: -10,
    color: primary
  }
}));

export const HiddenItemEllipsis = withShapeRenderContext((props) => {
  const classes = useShapeViewerStyles();
  const {setShowAllLists} = useContext(ShapeExpandedContext);
  const {expandId} = props;
  return (<DiffToolTip placement="right" title="(Hidden) Click to Expand">
    <div className={classes.hiddenItem} onClick={() => setShowAllLists(expandId, true)}>{'⋯'}</div>
  </DiffToolTip>);
});


export const DiffToolTip = withStyles(theme => ({
  tooltip: {
    backgroundColor: '#2A3B72',
    color: 'rgba(247, 248, 240, 1)',
    boxShadow: theme.shadows[1],
    maxWidth: 200,
    fontSize: 11,
    fontWeight: 200,
    padding: 4,
  },
}))(Tooltip);
