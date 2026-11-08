<script setup lang="ts">
import PublicLayout from '@/layouts/PublicLayout.vue'
import ApiStatus from '../components/ApiStatus.vue'
</script>

<template>
  <PublicLayout title="like-zendesk">
    <p>Zendesk ライクなインシデント管理システム（開発中）</p>
    <p>
      <RouterLink to="/signup">
        テナントを作成する
      </RouterLink>
    </p>
    <ApiStatus />
  </PublicLayout>
</template>
