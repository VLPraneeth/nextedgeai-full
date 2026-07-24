import ReactDOM from 'react-dom';

import App from './App';
import * as serviceWorker from './service-worker';
import './index.css';

ReactDOM.render(<App />, document.getElementById('root'));

serviceWorker.register();
