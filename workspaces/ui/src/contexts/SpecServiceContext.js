import React from 'react'
import {GenericContextFactory} from './GenericContextFactory';

const {
  Context: SpecServiceContext,
  withContext: withSpecServiceContext
} = GenericContextFactory(null);

class SpecServiceStore extends React.Component {

  componentDidMount() {
    const {specService, specServiceEvents} = this.props;
    if (!specServiceEvents) {
      console.warn('I need specServiceEvents')
      debugger
    } else {
      specServiceEvents.on('events-updated', () => {
        this.forceUpdate()
      })
    }
  }

  render() {
    const {specService} = this.props;

    return (
      <SpecServiceContext.Provider value={{specService}}>
        {this.props.children}
      </SpecServiceContext.Provider>
    );
  }
}

export {
  withSpecServiceContext,
  SpecServiceContext,
  SpecServiceStore
};
