<template>
  <!-- ページはルートの params を setup で一度だけ読むので、パスが変わったら作り直す（同じ画面で別のチケットへ移るときなど） -->
  <RouterView :key="$route.path" />
</template>
