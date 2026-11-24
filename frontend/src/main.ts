import { createApp } from 'vue'
import App from './app/App.vue'
import { router } from './app/router'
import './app/styles.css'
import './app/forms.css'

createApp(App).use(router).mount('#app')
