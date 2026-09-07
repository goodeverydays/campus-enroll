<script setup lang="ts">
defineProps<{ status: string }>();

function tone(status: string): string {
  if (['SUCCESS', 'ACTIVE', 'OPEN', 'ENROLLMENT_OPEN'].includes(status)) return 'success';
  if (['FAILED', 'CANCELLED', 'WITHDRAWN'].includes(status)) return 'danger';
  if (['PENDING', 'PLANNED'].includes(status)) return 'warning';
  return 'neutral';
}
</script>

<template>
  <span class="status-chip" :class="`status-${tone(status)}`">{{ status }}</span>
</template>
