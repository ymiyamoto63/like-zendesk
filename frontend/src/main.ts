import Aura from '@primeuix/themes/aura'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import './style.css'

createApp(App)
  .use(createPinia())
  .use(router)
  .use(VueQueryPlugin)
  .use(PrimeVue, { theme: { preset: Aura } })
  .mount('#app')
