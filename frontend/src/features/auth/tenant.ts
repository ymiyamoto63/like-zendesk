import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, unwrap } from '@/api/client'
import type { components } from '@/api/schema'

export type TenantInfo = components['schemas']['TenantInfo']

/** URL のテナントを読み込む。存在しなければ 404 の画面に切り替える。 */
export function useTenant() {
  const route = useRoute()
  const router = useRouter()
  const slug = String(route.params.slug)
  const tenant = ref<TenantInfo | null>(null)

  onMounted(async () => {
    try {
      tenant.value = await unwrap(api.GET('/api/t/{slug}', { params: { path: { slug } } }))
    } catch {
      await router.replace({ name: 'not-found', params: { pathMatch: route.path.slice(1).split('/') } })
    }
  })

  return { slug, tenant }
}
