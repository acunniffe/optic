import React, {useContext} from 'react';
import Button from '@material-ui/core/Button';
import Dialog from '@material-ui/core/Dialog';
import DialogActions from '@material-ui/core/DialogActions';
import DialogContent from '@material-ui/core/DialogContent';
import DialogContentText from '@material-ui/core/DialogContentText';
import DialogTitle from '@material-ui/core/DialogTitle';
import Slide from '@material-ui/core/Slide';
import {NavigationContext} from '../../../contexts/NavigationContext';

const Transition = React.forwardRef(function Transition(props, ref) {
  return <Slide direction="up" ref={ref} {...props} />;
});

export default function MoreRecentCapture(props) {
  const navigationContext = useContext(NavigationContext)

  const handleClickOpen = () => navigationContext.pushRelative(props.target)

  const handleClose = () => props.dismiss()

  return (
      <Dialog
        open={props.open}
        TransitionComponent={Transition}
        keepMounted
        onClose={handleClose}
        aria-labelledby="alert-dialog-slide-title"
        aria-describedby="alert-dialog-slide-description"
      >
        <DialogTitle id="alert-dialog-slide-title">New Capture</DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-slide-description">
            Optic has observed a newer Capture than the one you are currently working on.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose} color="primary">
            Dismiss
          </Button>
          <Button onClick={handleClickOpen} color="primary">
            Review
          </Button>
        </DialogActions>
      </Dialog>
  );
}
