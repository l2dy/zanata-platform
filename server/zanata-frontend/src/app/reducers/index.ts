import { combineReducers } from 'redux'
import { routerReducer as routing } from 'react-router-redux'
import glossary from './glossary-reducer'
import explore from './explore-reducer'
import profile from './profile-reducer'
import common from './common-reducer'
import languages from './languages-reducer'
import projectVersion from './version-reducer'
import tmx from './tmx-reducer'
import admin from './admin-reducer'

// If you add a subtree, please update TopLevelState in state.ts
// TODO use TypeScript's advanced types to type this
const rootReducer = combineReducers({
  routing,
  explore,
  glossary,
  common,
  profile,
  languages,
  projectVersion,
  tmx,
  admin
})

export default rootReducer
