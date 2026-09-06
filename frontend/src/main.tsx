import { Provider } from 'react-redux'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'

import './index.css'
import App from './App.tsx'
import { createAppStore } from './app/store'
import { createLocalAnonymousUsageStorage } from './data/storage/anonymousUsageStorage'

const appStore = createAppStore({ anonymousUsageStorage: createLocalAnonymousUsageStorage() })

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={appStore}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Provider>
  </StrictMode>,
)
