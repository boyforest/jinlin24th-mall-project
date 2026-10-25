import { createSSRApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { useAuthStore } from '@/stores/auth'
import { getToken, getUserId, onAccountChange } from '@/utils/storage'
import { useCartStore } from '@/stores/cart'
import { useCoffeeCartStore } from '@/stores/coffeeCart'
export function createApp() {
  const app = createSSRApp(App)
  const pinia = createPinia()
  app.use(pinia)
  onAccountChange(() => {
    useAuthStore(pinia).$patch({ token: getToken(), userId: getToken() ? getUserId() || 0 : 0, profile: null })
    useCartStore(pinia).clear()
    useCoffeeCartStore(pinia).clear()
  })
  return { app }
}
