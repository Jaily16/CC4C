// // import { login, logout, getInfo } from '@/api/user'
// // import { getToken, setToken, removeToken } from '@/utils/auth'
// import { resetRouter } from '@/router'

// const getDefaultState = () => {
//   return {
//     token: getToken(),
//     name: '',
//     avatar: ''
//   }
// }

// const state = getDefaultState()

// const mutations = {
//   RESET_STATE: (state) => {
//     Object.assign(state, getDefaultState())
//   },
//   SET_TOKEN: (state, token) => {
//     state.token = token
//   },
//   SET_NAME: (state, name) => {
//     state.name = name
//   },
//   SET_AVATAR: (state, avatar) => {
//     state.avatar = avatar
//   }
// }

// const actions = {
//   // user login
//   login({ commit }, userInfo) {
//     const { username, password } = userInfo
//     return new Promise((resolve, reject) => {
//       login({ username: username.trim(), password: password }).then(response => {
//         const { data } = response
//         commit('SET_TOKEN', data.token)
//         setToken(data.token)
//         resolve()
//       }).catch(error => {
//         reject(error)
//       })
//     })
//   },

//   // get user info
//   getInfo({ commit, state }) {
//     return new Promise((resolve, reject) => {
//       getInfo(state.token).then(response => {
//         const { data } = response

//         if (!data) {
//           return reject('Verification failed, please Login again.')
//         }

//         const { name, avatar } = data

//         commit('SET_NAME', name)
//         commit('SET_AVATAR', avatar)
//         resolve(data)
//       }).catch(error => {
//         reject(error)
//       })
//     })
//   },

//   // user logout
//   logout({ commit, state }) {
//     return new Promise((resolve, reject) => {
//       logout(state.token).then(() => {
//         removeToken() // must remove  token  first
//         resetRouter()
//         commit('RESET_STATE')
//         resolve()
//       }).catch(error => {
//         reject(error)
//       })
//     })
//   },

//   // remove token
//   resetToken({ commit }) {
//     return new Promise(resolve => {
//       removeToken() // must remove  token  first
//       commit('RESET_STATE')
//       resolve()
//     })
//   }
// }

// export default {
//   namespaced: true,
//   state,
//   mutations,
//   actions
// }


import api from '@/plugins/axiosInstance'

const getDefaultState = () => {
    return {
        authenticated: false,
        hydrated: false,
        role: '',
        id: sessionStorage.getItem('id') ? sessionStorage.getItem('id') : '',
        name: sessionStorage.getItem('name') ? sessionStorage.getItem('name') : '',
        email: sessionStorage.getItem('email') ? sessionStorage.getItem('email') : '',
        major: sessionStorage.getItem('major') ? sessionStorage.getItem('major') : 0,
        language: sessionStorage.getItem('language') ? sessionStorage.getItem('language') : 1,
        avatar: sessionStorage.getItem('avatar') ? sessionStorage.getItem('avatar') : '',
    }
}

const state = getDefaultState()

const mutations = {
    RESET_STATE: (state) => {
        sessionStorage.setItem('id', '');
        sessionStorage.setItem('name', '');
        sessionStorage.setItem('email', '');
        sessionStorage.setItem('major', 0);
        sessionStorage.setItem('language', 1);
        sessionStorage.setItem('avatar', '');
        Object.assign(state, getDefaultState())
        state.hydrated = true
    },
    SET_HYDRATED: (state, hydrated) => {
        state.hydrated = hydrated
    },
    SET_SESSION: (state, session) => {
        state.authenticated = Boolean(session?.authenticated)
        state.role = session?.role || ''
        state.id = session?.actorId || ''
        state.name = session?.displayName || ''
        sessionStorage.setItem('id', state.id)
        sessionStorage.setItem('name', state.name)
    },
    SET_ID: (state, id) => {
        state.id = id
        sessionStorage.setItem('id', id);
    },
    SET_NAME: (state, name) => {
        // state.name = name
        state.name = name
        sessionStorage.setItem('name', name);
    },
    SET_EMAIL: (state, email) => {
        state.email = email
        sessionStorage.setItem('email', email);
    },
    SET_MAJOR: (state, major) => {
        state.major = major
        sessionStorage.setItem('major', major);
    },
    SET_LANGUAGE: (state, language) => {
        state.language = language
        sessionStorage.setItem('language', language);
    },
    SET_AVATAR: (state, avatar) => {
        state.avatar = avatar
        sessionStorage.setItem('avatar', avatar);
    }
}

const actions = {
    async hydrateSession({ state, commit }, { force = false } = {}) {
        if (state.hydrated && !force) return state
        const response = await api.get('/auth/session')
        const session = response.data.data
        if (!session?.authenticated) {
            commit('RESET_STATE')
            return state
        }
        commit('SET_SESSION', session)
        if (session.role === 'USER') {
            const userResponse = await api.get('/users/me')
            const user = userResponse.data.data
            commit('SET_ID', user.id)
            commit('SET_NAME', user.name)
            commit('SET_EMAIL', user.email)
            commit('SET_MAJOR', user.major)
            commit('SET_LANGUAGE', user.language)
            commit('SET_AVATAR', user.avatar)
        }
        commit('SET_HYDRATED', true)
        return state
    },
}

export default {
    state,
    mutations,
    actions,
}
