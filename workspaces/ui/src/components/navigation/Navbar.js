import React, { useMemo } from 'react';
import Drawer from '@material-ui/core/Drawer';
import { makeStyles } from '@material-ui/core/styles';
import { routerPaths } from '../../RouterPaths';
import { Link, Route, Switch } from 'react-router-dom';
import IconButton from '@material-ui/core/IconButton';
import CodeIcon from '@material-ui/icons/Code';
import DescriptionIcon from '@material-ui/icons/Description';
import ChangeHistoryIcon from '@material-ui/icons/ChangeHistory';
import PolicyIcon from '@material-ui/icons/Policy';
import { LightTooltip } from '../tooltips/LightTooltip';

const drawerWidth = 270;

const useStyles = makeStyles({
  drawer: {
    width: drawerWidth,
    flexShrink: 0
  },
  miniDrawer: {
    width: 55,
    flexShrink: 0
  },
  drawerPaper: {
    width: drawerWidth,
    backgroundColor: '#1B2958',
    display: 'flex',
    flexDirection: 'row'
  },
  miniDrawerPaper: {
    backgroundColor: '#1B2958',
    display: 'flex',
    flexDirection: 'row'
  },
  topLevel: {
    width: 55,
    // backgroundColor: '#2b3966',
    overflow: 'hidden',
    borderRight: '1px solid #3F5597',
    display: 'flex',
    alignItems: 'center',
    flexDirection: 'column'
  },
  navButton: {
    marginTop: 6
  },
  opticLogo: {
    marginTop: 5
  },
  mainSection: {
    flex: 1
  }
});

export default function Navbar(props) {
  const { baseUrl, entryBasePath } = props;
  const classes = useStyles();

  const menuItems = useMemo(
    () => [
      {
        name: 'Review Diff',
        icon: <ChangeHistoryIcon style={{ color: '#e2e2e2' }} />,
        link: `${baseUrl}/diff`
      },
      {
        name: 'Specification',
        icon: <DescriptionIcon style={{ color: '#e2e2e2' }} />,
        link: `${baseUrl}/documentation`
      },
      {
        name: 'Live Contract Testing',
        icon: <PolicyIcon style={{ color: '#e2e2e2' }} />,
        link: routerPaths.testingDashboard(baseUrl)
      }
    ],
    [baseUrl]
  );

  return (
    <Drawer
      id="navbar"
      elevation={2}
      className={props.mini ? classes.miniDrawer : classes.drawer}
      variant={'permanent'}
      classes={{
        paper: props.mini ? classes.miniDrawerPaper : classes.drawerPaper
      }}
      anchor="left"
    >
      <div
        className={classes.topLevel}
        style={props.mini && { borderRight: 'none' }}
      >
        <img src="/optic-logo.svg" width={50} className={classes.opticLogo} />
        {menuItems.map((i) => (
          <LightTooltip
            title={i.name}
            component={Link}
            to={i.link}
            placement="right"
          >
            <IconButton className={classes.navButton}>{i.icon}</IconButton>
          </LightTooltip>
        ))}
      </div>
      {props.mini ? null : (
        <div className={classes.mainSection}>{props.children}</div>
      )}
    </Drawer>
  );
}
