// import Vue from 'vue'
// import Vuex from 'vuex'
// import getters from './getters'
import user from './modules/user'

// Vue.use(Vuex)

// const store = new Vuex.Store({
//   modules: {
//     user
//   },
//   getters
// })

// export default store

import { createStore } from 'vuex'

export default createStore({
    state: {
        haha: 'test'
    },
    mutations: {
    },
    actions: {
    },
    modules: {
        user
    }
})
